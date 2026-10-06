package com.relay.security.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Application-level envelope encryption with the hybrid post-quantum KEM.
 *
 * <pre>
 *   envelope := "RLE1" || frame(key_id, encapsulation, aead_ciphertext)
 *   aad      := "relay/envelope/v1|" || context || "|" || key_id
 * </pre>
 *
 * The context (e.g. {@code webhook_secret:wf_support_triage}) is bound into both the KDF and the
 * AEAD, so an envelope copied onto another row fails to decrypt.
 */
public class EnvelopeCipher {

  private static final byte[] MAGIC = "RLE1".getBytes(StandardCharsets.US_ASCII);

  private final KeyRing keys;
  private final PqCryptoService pq;

  /**
   * Creates the cipher.
   *
   * @param keys key ring
   * @param pq crypto primitives
   */
  public EnvelopeCipher(KeyRing keys, PqCryptoService pq) {
    this.keys = keys;
    this.pq = pq;
  }

  /**
   * Encrypts to the active KEM key.
   *
   * @param plaintext secret bytes
   * @param context row-binding context
   * @return the envelope
   */
  public byte[] seal(byte[] plaintext, String context) {
    KeyRing.Key key = keys.activeKem();
    byte[] ctx = context.getBytes(StandardCharsets.UTF_8);
    PqCryptoService.Encapsulation enc = pq.encapsulate(key.publicKey(), ctx);
    byte[] sealed = pq.aeadSeal(enc.dataKey(), plaintext, aad(context, key.keyId()));
    return Bytes.concat(
        MAGIC,
        Bytes.frame(key.keyId().getBytes(StandardCharsets.UTF_8), enc.encapsulation(), sealed));
  }

  /**
   * Decrypts an envelope with whichever (active or retired) key sealed it.
   *
   * @param envelope bytes from {@link #seal(byte[], String)}
   * @param context the same context
   * @return plaintext
   */
  public byte[] open(byte[] envelope, String context) {
    List<byte[]> parts = parse(envelope);
    String keyId = new String(parts.get(0), StandardCharsets.UTF_8);
    KeyRing.Key key =
        keys.byId(keyId).orElseThrow(() -> new CryptoException("unknown KEM key " + keyId));
    byte[] dataKey =
        pq.decapsulate(key.privateKey(), parts.get(1), context.getBytes(StandardCharsets.UTF_8));
    return pq.aeadOpen(dataKey, parts.get(2), aad(context, keyId));
  }

  /**
   * The KEM key id an envelope was sealed with (for rotation bookkeeping).
   *
   * @param envelope envelope bytes
   * @return key id
   */
  public String keyIdOf(byte[] envelope) {
    return new String(parse(envelope).get(0), StandardCharsets.UTF_8);
  }

  private static List<byte[]> parse(byte[] envelope) {
    if (envelope == null
        || envelope.length < MAGIC.length
        || !Arrays.equals(Arrays.copyOf(envelope, MAGIC.length), MAGIC)) {
      throw new CryptoException("not a Relay envelope");
    }
    List<byte[]> parts = Bytes.unframe(Arrays.copyOfRange(envelope, MAGIC.length, envelope.length));
    if (parts.size() != 3) {
      throw new CryptoException("malformed envelope");
    }
    return parts;
  }

  private static byte[] aad(String context, String keyId) {
    return ("relay/envelope/v1|" + context + "|" + keyId).getBytes(StandardCharsets.UTF_8);
  }
}
