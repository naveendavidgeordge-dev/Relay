package com.relay.security.crypto;

/**
 * Post-quantum cryptographic primitives behind one interface so the engine can run against a fast,
 * deterministic fake in tests.
 *
 * <ul>
 *   <li><b>Hybrid KEM:</b> ML-KEM-768 (FIPS 203) + X25519. The two shared secrets are combined with
 *       HKDF-SHA3-256, binding both ciphertexts and the recipient's classical key, so the derived
 *       key is safe as long as <em>either</em> primitive holds ("store-now-decrypt-later"
 *       resistance without betting everything on a young algorithm).
 *   <li><b>AEAD:</b> AES-256-GCM, random 96-bit nonce per record.
 *   <li><b>Signatures:</b> ML-DSA-65 (FIPS 204).
 * </ul>
 *
 * <p>Keys cross this interface as encoded bytes (X.509 / PKCS#8 for the JCA impl) so persisted
 * keys are implementation-agnostic. See DESIGN/05-security-and-crypto.md.
 */
public interface PqCryptoService {

  /** Name of the hybrid KEM, recorded with keys. */
  String kemAlgorithm();

  /** Name of the signature scheme, recorded with signatures. */
  String signatureAlgorithm();

  /** Generates a hybrid KEM key pair. */
  KeyPairBytes generateKemKeyPair();

  /**
   * Encapsulates a fresh 32-byte data key to a recipient.
   *
   * @param recipientPublicKey encoded hybrid public key
   * @param context domain-separation context mixed into the KDF (e.g. {@code webhook_secret:wf_x})
   * @return the data key and the encapsulation to store with the ciphertext
   */
  Encapsulation encapsulate(byte[] recipientPublicKey, byte[] context);

  /**
   * Recovers the data key.
   *
   * @param recipientPrivateKey encoded hybrid private key
   * @param encapsulation bytes from {@link #encapsulate(byte[], byte[])}
   * @param context the same context used to encapsulate
   * @return the 32-byte data key
   */
  byte[] decapsulate(byte[] recipientPrivateKey, byte[] encapsulation, byte[] context);

  /** Generates an ML-DSA-65 key pair. */
  KeyPairBytes generateSigningKeyPair();

  /**
   * Signs a payload.
   *
   * @param privateKey encoded signing key
   * @param payload bytes to sign
   * @return signature
   */
  byte[] sign(byte[] privateKey, byte[] payload);

  /**
   * Verifies a signature; never throws for bad input.
   *
   * @param publicKey encoded verification key
   * @param payload signed bytes
   * @param signature signature
   * @return true if valid
   */
  boolean verify(byte[] publicKey, byte[] payload, byte[] signature);

  /**
   * AES-256-GCM encryption.
   *
   * @param key 32-byte key
   * @param plaintext data
   * @param aad associated data (authenticated, not encrypted)
   * @return {@code nonce(12) || ciphertext || tag(16)}
   */
  byte[] aeadSeal(byte[] key, byte[] plaintext, byte[] aad);

  /**
   * AES-256-GCM decryption.
   *
   * @param key 32-byte key
   * @param sealed output of {@link #aeadSeal(byte[], byte[], byte[])}
   * @param aad the same associated data
   * @return plaintext
   * @throws CryptoException if authentication fails
   */
  byte[] aeadOpen(byte[] key, byte[] sealed, byte[] aad);

  /**
   * An encoded key pair.
   *
   * @param publicKey encoded public key
   * @param privateKey encoded private key
   */
  record KeyPairBytes(byte[] publicKey, byte[] privateKey) {
    /** Canonical constructor; copies arrays. */
    public KeyPairBytes {
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
  }

  /**
   * Result of a KEM encapsulation.
   *
   * @param dataKey 32-byte symmetric key (never stored)
   * @param encapsulation bytes stored alongside the ciphertext
   */
  record Encapsulation(byte[] dataKey, byte[] encapsulation) {
    /** Canonical constructor; copies arrays. */
    public Encapsulation {
      dataKey = dataKey.clone();
      encapsulation = encapsulation.clone();
    }

    @Override
    public byte[] dataKey() {
      return dataKey.clone();
    }

    @Override
    public byte[] encapsulation() {
      return encapsulation.clone();
    }
  }
}
