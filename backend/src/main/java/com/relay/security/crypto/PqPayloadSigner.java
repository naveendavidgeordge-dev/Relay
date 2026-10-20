package com.relay.security.crypto;

/**
 * {@link PayloadSigner} backed by the key ring's ML-DSA-65 signing key. Used to sign published
 * definition versions, run snapshots and approval decisions; verification on read powers the
 * {@code signature_valid} field of runs.
 */
public class PqPayloadSigner implements PayloadSigner {

  private final KeyRing keys;
  private final PqCryptoService pq;

  /**
   * Creates the signer.
   *
   * @param keys key ring
   * @param pq crypto primitives
   */
  public PqPayloadSigner(KeyRing keys, PqCryptoService pq) {
    this.keys = keys;
    this.pq = pq;
  }

  @Override
  public Signature sign(byte[] payload) {
    KeyRing.Key key = keys.activeSigning();
    return new Signature(key.keyId(), pq.signatureAlgorithm(), pq.sign(key.privateKey(), payload));
  }

  @Override
  public boolean verify(byte[] payload, String keyId, byte[] signature) {
    if (signature == null) {
      return false;
    }
    return keys.byId(keyId)
        .filter(k -> KeyRing.SIGNING.equals(k.purpose()))
        .map(k -> pq.verify(k.publicKey(), payload, signature))
        .orElse(false);
  }
}
