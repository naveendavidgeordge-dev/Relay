package com.relay.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Production {@link PqCryptoService} on the BouncyCastle provider (ML-KEM per FIPS 203, ML-DSA per
 * FIPS 204) and the JDK's X25519 and AES-GCM.
 *
 * <p>KEM combiner (X-Wing style):
 *
 * <pre>
 *   ss_pq, ct_pq   = ML-KEM-768.Encaps(pk_pq)
 *   eph            = X25519.KeyGen();  ss_ec = X25519(eph.sk, pk_ec)
 *   salt           = SHA3-256(ct_pq || eph.pk || pk_ec)
 *   data_key       = HKDF-SHA3-256(ikm = ss_pq || ss_ec, salt, info = "relay/hybrid-kem/v1|" || context, L = 32)
 *   encapsulation  = frame(ct_pq, eph.pk)
 * </pre>
 */
public final class BouncyCastlePqCryptoService implements PqCryptoService {

  private static final String BC = BouncyCastleProvider.PROVIDER_NAME;
  private static final byte[] KDF_INFO = "relay/hybrid-kem/v1|".getBytes(StandardCharsets.UTF_8);
  private static final int GCM_NONCE = 12;
  private static final int GCM_TAG_BITS = 128;

  private final SecureRandom random = new SecureRandom();

  /** Registers the BouncyCastle provider if needed. */
  public BouncyCastlePqCryptoService() {
    if (Security.getProvider(BC) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  @Override
  public String kemAlgorithm() {
    return "ML-KEM-768+X25519/HKDF-SHA3-256";
  }

  @Override
  public String signatureAlgorithm() {
    return "ML-DSA-65";
  }

  // ---------------------------------------------------------------- KEM

  @Override
  public KeyPairBytes generateKemKeyPair() {
    try {
      KeyPairGenerator pq = KeyPairGenerator.getInstance("ML-KEM", BC);
      pq.initialize(MLKEMParameterSpec.ml_kem_768, random);
      KeyPair pqPair = pq.generateKeyPair();
      KeyPair ecPair = KeyPairGenerator.getInstance("X25519").generateKeyPair();
      return new KeyPairBytes(
          Bytes.frame(pqPair.getPublic().getEncoded(), ecPair.getPublic().getEncoded()),
          Bytes.frame(
              pqPair.getPrivate().getEncoded(),
              ecPair.getPrivate().getEncoded(),
              ecPair.getPublic().getEncoded()));
    } catch (GeneralSecurityException e) {
      throw new CryptoException("KEM key generation failed", e);
    }
  }

  @Override
  public Encapsulation encapsulate(byte[] recipientPublicKey, byte[] context) {
    try {
      List<byte[]> parts = Bytes.unframe(recipientPublicKey);
      PublicKey pqPub = KeyFactory.getInstance("ML-KEM", BC)
          .generatePublic(new X509EncodedKeySpec(parts.get(0)));
      byte[] ecPubBytes = parts.get(1);
      PublicKey ecPub = KeyFactory.getInstance("X25519")
          .generatePublic(new X509EncodedKeySpec(ecPubBytes));

      KeyGenerator kemGen = KeyGenerator.getInstance("ML-KEM", BC);
      kemGen.init(new KEMGenerateSpec(pqPub, "AES"), random);
      SecretKeyWithEncapsulation pqSecret = (SecretKeyWithEncapsulation) kemGen.generateKey();
      byte[] ssPq = pqSecret.getEncoded();
      byte[] ctPq = pqSecret.getEncapsulation();

      KeyPair eph = KeyPairGenerator.getInstance("X25519").generateKeyPair();
      byte[] ssEc = x25519(eph.getPrivate(), ecPub);
      byte[] ephPub = eph.getPublic().getEncoded();

      byte[] key = combine(ssPq, ssEc, ctPq, ephPub, ecPubBytes, context);
      wipe(ssPq, ssEc);
      return new Encapsulation(key, Bytes.frame(ctPq, ephPub));
    } catch (GeneralSecurityException | IndexOutOfBoundsException e) {
      throw new CryptoException("KEM encapsulation failed", e);
    }
  }

  @Override
  public byte[] decapsulate(byte[] recipientPrivateKey, byte[] encapsulation, byte[] context) {
    try {
      List<byte[]> keyParts = Bytes.unframe(recipientPrivateKey);
      PrivateKey pqPriv = KeyFactory.getInstance("ML-KEM", BC)
          .generatePrivate(new PKCS8EncodedKeySpec(keyParts.get(0)));
      PrivateKey ecPriv = KeyFactory.getInstance("X25519")
          .generatePrivate(new PKCS8EncodedKeySpec(keyParts.get(1)));
      byte[] ecPubBytes = keyParts.get(2);

      List<byte[]> encParts = Bytes.unframe(encapsulation);
      byte[] ctPq = encParts.get(0);
      byte[] ephPubBytes = encParts.get(1);

      KeyGenerator kemGen = KeyGenerator.getInstance("ML-KEM", BC);
      kemGen.init(new KEMExtractSpec(pqPriv, ctPq, "AES"));
      byte[] ssPq = ((SecretKeyWithEncapsulation) kemGen.generateKey()).getEncoded();

      PublicKey ephPub = KeyFactory.getInstance("X25519")
          .generatePublic(new X509EncodedKeySpec(ephPubBytes));
      byte[] ssEc = x25519(ecPriv, ephPub);

      byte[] key = combine(ssPq, ssEc, ctPq, ephPubBytes, ecPubBytes, context);
      wipe(ssPq, ssEc);
      return key;
    } catch (GeneralSecurityException | IndexOutOfBoundsException e) {
      throw new CryptoException("KEM decapsulation failed", e);
    }
  }

  private static byte[] x25519(PrivateKey priv, PublicKey pub) throws GeneralSecurityException {
    KeyAgreement ka = KeyAgreement.getInstance("X25519");
    ka.init(priv);
    ka.doPhase(pub, true);
    return ka.generateSecret();
  }

  private static byte[] combine(
      byte[] ssPq, byte[] ssEc, byte[] ctPq, byte[] ephPub, byte[] recipientEcPub, byte[] context) {
    SHA3Digest saltDigest = new SHA3Digest(256);
    byte[] saltInput = Bytes.concat(ctPq, ephPub, recipientEcPub);
    saltDigest.update(saltInput, 0, saltInput.length);
    byte[] salt = new byte[32];
    saltDigest.doFinal(salt, 0);

    HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA3Digest(256));
    byte[] ikm = Bytes.concat(ssPq, ssEc);
    hkdf.init(new HKDFParameters(ikm, salt, Bytes.concat(KDF_INFO, context)));
    byte[] out = new byte[32];
    hkdf.generateBytes(out, 0, out.length);
    wipe(ikm);
    return out;
  }

  // ---------------------------------------------------------------- signatures

  @Override
  public KeyPairBytes generateSigningKeyPair() {
    try {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("ML-DSA", BC);
      kpg.initialize(MLDSAParameterSpec.ml_dsa_65, random);
      KeyPair kp = kpg.generateKeyPair();
      return new KeyPairBytes(kp.getPublic().getEncoded(), kp.getPrivate().getEncoded());
    } catch (GeneralSecurityException e) {
      throw new CryptoException("ML-DSA key generation failed", e);
    }
  }

  @Override
  public byte[] sign(byte[] privateKey, byte[] payload) {
    try {
      PrivateKey key =
          KeyFactory.getInstance("ML-DSA", BC).generatePrivate(new PKCS8EncodedKeySpec(privateKey));
      Signature s = Signature.getInstance("ML-DSA", BC);
      s.initSign(key, random);
      s.update(payload);
      return s.sign();
    } catch (GeneralSecurityException e) {
      throw new CryptoException("ML-DSA signing failed", e);
    }
  }

  @Override
  public boolean verify(byte[] publicKey, byte[] payload, byte[] signature) {
    if (publicKey == null || payload == null || signature == null) {
      return false;
    }
    try {
      PublicKey key =
          KeyFactory.getInstance("ML-DSA", BC).generatePublic(new X509EncodedKeySpec(publicKey));
      Signature s = Signature.getInstance("ML-DSA", BC);
      s.initVerify(key);
      s.update(payload);
      return s.verify(signature);
    } catch (GeneralSecurityException | RuntimeException e) {
      return false;
    }
  }

  // ---------------------------------------------------------------- AEAD

  @Override
  public byte[] aeadSeal(byte[] key, byte[] plaintext, byte[] aad) {
    try {
      byte[] nonce = new byte[GCM_NONCE];
      random.nextBytes(nonce);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
      c.updateAAD(aad);
      return Bytes.concat(nonce, c.doFinal(plaintext));
    } catch (GeneralSecurityException e) {
      throw new CryptoException("AES-GCM encryption failed", e);
    }
  }

  @Override
  public byte[] aeadOpen(byte[] key, byte[] sealed, byte[] aad) {
    if (sealed.length < GCM_NONCE + GCM_TAG_BITS / 8) {
      throw new CryptoException("ciphertext too short");
    }
    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(GCM_TAG_BITS, Arrays.copyOfRange(sealed, 0, GCM_NONCE)));
      c.updateAAD(aad);
      return c.doFinal(Arrays.copyOfRange(sealed, GCM_NONCE, sealed.length));
    } catch (GeneralSecurityException e) {
      throw new CryptoException("AES-GCM authentication failed", e);
    }
  }

  private static void wipe(byte[]... arrays) {
    for (byte[] a : arrays) {
      Arrays.fill(a, (byte) 0);
    }
  }
}
