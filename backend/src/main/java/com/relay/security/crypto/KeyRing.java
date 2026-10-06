package com.relay.security.crypto;

import com.relay.support.Ids;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistent key management over {@code crypto_keys}.
 *
 * <p>Private keys are wrapped with AES-256-GCM under the master KEK ({@code RELAY_MASTER_KEY}; AAD
 * = key id) before they touch the database. Exactly one {@code active} key exists per purpose
 * (partial unique index); {@link #rotate(String)} retires the current key, which stays available
 * for decrypting old envelopes and verifying old signatures. API and worker processes converge on
 * the same keys because creation is {@code INSERT … ON CONFLICT DO NOTHING} followed by a re-read.
 */
public class KeyRing {

  /** KEM purpose. */
  public static final String KEM = "kem";

  /** Signing purpose. */
  public static final String SIGNING = "signing";

  private static final Logger log = LoggerFactory.getLogger(KeyRing.class);
  private static final String DEV_MASTER_KEY = "ZGV2LW9ubHktbWFzdGVyLWtleS1jaGFuZ2UtbWUtMzI=";

  private final JdbcTemplate jdbc;
  private final PqCryptoService pq;
  private final byte[] kek;
  private final Map<String, Key> cache = new ConcurrentHashMap<>();
  private final Map<String, String> activeIds = new ConcurrentHashMap<>();

  /**
   * Creates the key ring.
   *
   * @param jdbc JDBC template
   * @param pq crypto primitives
   * @param masterKeyBase64 base64 32-byte KEK
   */
  public KeyRing(JdbcTemplate jdbc, PqCryptoService pq, String masterKeyBase64) {
    this.jdbc = jdbc;
    this.pq = pq;
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(masterKeyBase64 == null ? "" : masterKeyBase64.strip());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("RELAY_MASTER_KEY is not valid base64", e);
    }
    if (decoded.length != 32) {
      throw new IllegalStateException(
          "RELAY_MASTER_KEY must decode to exactly 32 bytes (got " + decoded.length + ")");
    }
    if (DEV_MASTER_KEY.equals(masterKeyBase64.strip())) {
      log.warn("RELAY_MASTER_KEY is the development default; set a real key outside local dev");
    }
    this.kek = decoded;
  }

  /** The active KEM key (created on first use). */
  public Key activeKem() {
    return active(KEM);
  }

  /** The active signing key (created on first use). */
  public Key activeSigning() {
    return active(SIGNING);
  }

  /**
   * Any key (active or retired) by id.
   *
   * @param keyId key id
   * @return the key, unwrapped
   */
  public Optional<Key> byId(String keyId) {
    if (keyId == null) {
      return Optional.empty();
    }
    Key cached = cache.get(keyId);
    if (cached != null) {
      return Optional.of(cached);
    }
    List<Key> rows =
        jdbc.query(
            "SELECT key_id, purpose, algorithm, public_key, private_key_wrapped FROM crypto_keys"
                + " WHERE key_id = ?",
            (rs, i) ->
                unwrap(
                    rs.getString("key_id"),
                    rs.getString("purpose"),
                    rs.getString("algorithm"),
                    rs.getBytes("public_key"),
                    rs.getBytes("private_key_wrapped")),
            keyId);
    rows.forEach(k -> cache.put(k.keyId(), k));
    return rows.stream().findFirst();
  }

  /**
   * Retires the active key of a purpose and creates a new one.
   *
   * @param purpose {@link #KEM} or {@link #SIGNING}
   * @return the new key id
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public String rotate(String purpose) {
    jdbc.update(
        "UPDATE crypto_keys SET status = 'retired', retired_at = now()"
            + " WHERE purpose = ? AND status = 'active'",
        purpose);
    activeIds.remove(purpose);
    return active(purpose).keyId();
  }

  private Key active(String purpose) {
    String id = activeIds.get(purpose);
    if (id != null) {
      Optional<Key> k = byId(id);
      if (k.isPresent()) {
        return k.get();
      }
    }
    Optional<Key> existing = loadActive(purpose);
    if (existing.isEmpty()) {
      create(purpose);
      existing = loadActive(purpose);
    }
    Key key =
        existing.orElseThrow(() -> new CryptoException("no active " + purpose + " key after create"));
    activeIds.put(purpose, key.keyId());
    return key;
  }

  private Optional<Key> loadActive(String purpose) {
    List<String> ids =
        jdbc.queryForList(
            "SELECT key_id FROM crypto_keys WHERE purpose = ? AND status = 'active'",
            String.class,
            purpose);
    return ids.isEmpty() ? Optional.empty() : byId(ids.get(0));
  }

  private void create(String purpose) {
    PqCryptoService.KeyPairBytes pair =
        KEM.equals(purpose) ? pq.generateKemKeyPair() : pq.generateSigningKeyPair();
    String algorithm = KEM.equals(purpose) ? pq.kemAlgorithm() : pq.signatureAlgorithm();
    String keyId = (KEM.equals(purpose) ? "kem_" : "sig_") + Ids.ulid();
    byte[] wrapped = pq.aeadSeal(kek, pair.privateKey(), aad(keyId));
    int inserted =
        jdbc.update(
            "INSERT INTO crypto_keys (key_id, purpose, algorithm, public_key, private_key_wrapped,"
                + " status) VALUES (?, ?, ?, ?, ?, 'active')"
                + " ON CONFLICT (purpose) WHERE status = 'active' DO NOTHING",
            keyId,
            purpose,
            algorithm,
            pair.publicKey(),
            wrapped);
    if (inserted == 1) {
      log.info("Created {} key {} ({})", purpose, keyId, algorithm);
    }
  }

  private Key unwrap(
      String keyId, String purpose, String algorithm, byte[] publicKey, byte[] wrapped) {
    byte[] priv = pq.aeadOpen(kek, wrapped, aad(keyId));
    return new Key(keyId, purpose, algorithm, publicKey, priv);
  }

  private static byte[] aad(String keyId) {
    return ("relay/key-wrap/v1|" + keyId).getBytes(StandardCharsets.UTF_8);
  }

  /**
   * An unwrapped key held in memory.
   *
   * @param keyId key id
   * @param purpose {@code kem} / {@code signing}
   * @param algorithm algorithm name
   * @param publicKey encoded public key
   * @param privateKey encoded private key
   */
  public record Key(
      String keyId, String purpose, String algorithm, byte[] publicKey, byte[] privateKey) {

    /** Canonical constructor; copies arrays. */
    public Key {
      publicKey = publicKey.clone();
      privateKey = privateKey.clone();
    }

    @Override
    public byte[] publicKey() {
      return publicKey.clone();
    }

    @Override
    public byte[] privateKey() {
      return privateKey.clone();
    }

    @Override
    public String toString() {
      return "Key[" + keyId + ", " + purpose + ", " + algorithm + "]";
    }
  }
}
