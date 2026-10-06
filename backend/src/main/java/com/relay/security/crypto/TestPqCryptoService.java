package com.relay.security.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Deterministic, dependency-free stand-in for {@link PqCryptoService}. <b>NOT SECURE — tests
 * only</b> (selected with {@code relay.crypto.provider=test}).
 *
 * <ul>
 *   <li>Keys are counter-derived SHA-256 values; public key == private key.
 *   <li>"KEM" derives the data key as HMAC-SHA256(key, context).
 *   <li>Signatures are HMAC-SHA256(key, payload).
 *   <li>AEAD is real AES-256-GCM with a nonce derived from the inputs (deterministic output).
 * </ul>
 *
 * Its value is speed and reproducibility in engine tests while exercising the exact same calling
 * code paths (key ring, envelopes, signature checks) as production.
 */
public final class TestPqCryptoService implements PqCryptoService {

  private final AtomicLong counter = new AtomicLong();

  @Override
  public String kemAlgorithm() {
    return "TEST-KEM";
  }

  @Override
  public String signatureAlgorithm() {
    return "TEST-HMAC";
  }

  @Override
  public KeyPairBytes generateKemKeyPair() {
    byte[] k = derive("kem-" + counter.incrementAndGet());
    return new KeyPairBytes(k, k);
  }

  @Override
  public Encapsulation encapsulate(byte[] recipientPublicKey, byte[] context) {
    return new Encapsulation(hmac(recipientPublicKey, context), "test-encapsulation".getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public byte[] decapsulate(byte[] recipientPrivateKey, byte[] encapsulation, byte[] context) {
    return hmac(recipientPrivateKey, context);
  }

  @Override
  public KeyPairBytes generateSigningKeyPair() {
    byte[] k = derive("sig-" + counter.incrementAndGet());
    return new KeyPairBytes(k, k);
  }

  @Override
  public byte[] sign(byte[] privateKey, byte[] payload) {
    return hmac(privateKey, payload);
  }

  @Override
  public boolean verify(byte[] publicKey, byte[] payload, byte[] signature) {
    if (publicKey == null || payload == null || signature == null) {
      return false;
    }
    return MessageDigest.isEqual(hmac(publicKey, payload), signature);
  }

  @Override
  public byte[] aeadSeal(byte[] key, byte[] plaintext, byte[] aad) {
    byte[] nonce = Arrays.copyOf(Bytes.sha256(Bytes.concat(key, plaintext, aad)), 12);
    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
      c.updateAAD(aad);
      return Bytes.concat(nonce, c.doFinal(plaintext));
    } catch (GeneralSecurityException e) {
      throw new CryptoException("test AEAD seal failed", e);
    }
  }

  @Override
  public byte[] aeadOpen(byte[] key, byte[] sealed, byte[] aad) {
    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(128, Arrays.copyOfRange(sealed, 0, 12)));
      c.updateAAD(aad);
      return c.doFinal(Arrays.copyOfRange(sealed, 12, sealed.length));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new CryptoException("test AEAD authentication failed", e);
    }
  }

  private static byte[] derive(String label) {
    return Bytes.sha256(
        ByteBuffer.allocate(64).put(label.getBytes(StandardCharsets.UTF_8)).array());
  }

  private static byte[] hmac(byte[] key, byte[] data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data);
    } catch (GeneralSecurityException e) {
      throw new CryptoException("HMAC failed", e);
    }
  }
}
