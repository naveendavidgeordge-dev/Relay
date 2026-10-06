package com.relay.security.crypto;

/**
 * Port for tamper-evidence signatures over canonical JSON (definition versions, run snapshots,
 * approval decisions). Production signs with ML-DSA-65 (FIPS 204).
 */
public interface PayloadSigner {

  /**
   * Signs a payload with the active signing key.
   *
   * @param payload canonical bytes
   * @return signature plus the key id needed to verify it later
   */
  Signature sign(byte[] payload);

  /**
   * Verifies a signature produced by {@link #sign(byte[])} with any (active or retired) key.
   *
   * @param payload canonical bytes
   * @param keyId key id stored with the signature
   * @param signature signature bytes
   * @return true if valid; false if invalid or the key is unknown
   */
  boolean verify(byte[] payload, String keyId, byte[] signature);

  /**
   * A detached signature.
   *
   * @param keyId signing key id
   * @param algorithm algorithm name, e.g. {@code ML-DSA-65}
   * @param bytes signature bytes
   */
  record Signature(String keyId, String algorithm, byte[] bytes) {

    /** Canonical constructor; copies the bytes. */
    public Signature {
      bytes = bytes.clone();
    }

    /** A copy of the signature bytes. */
    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }
}
