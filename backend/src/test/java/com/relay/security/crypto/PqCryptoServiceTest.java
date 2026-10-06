package com.relay.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Round-trip and tamper tests for both {@link PqCryptoService} implementations. The BouncyCastle
 * cases exercise real ML-KEM-768 + X25519 (FIPS 203) and ML-DSA-65 (FIPS 204).
 */
class PqCryptoServiceTest {

  private static final byte[] CTX = "webhook_secret:wf_test".getBytes(StandardCharsets.UTF_8);

  static Stream<PqCryptoService> impls() {
    return Stream.of(new BouncyCastlePqCryptoService(), new TestPqCryptoService());
  }

  @ParameterizedTest
  @MethodSource("impls")
  void kemRoundTripYieldsTheSameDataKey(PqCryptoService pq) {
    PqCryptoService.KeyPairBytes kp = pq.generateKemKeyPair();
    PqCryptoService.Encapsulation enc = pq.encapsulate(kp.publicKey(), CTX);
    byte[] recovered = pq.decapsulate(kp.privateKey(), enc.encapsulation(), CTX);
    assertThat(enc.dataKey()).hasSize(32);
    assertThat(recovered).isEqualTo(enc.dataKey());
  }

  @ParameterizedTest
  @MethodSource("impls")
  void kemContextIsBoundIntoTheKey(PqCryptoService pq) {
    PqCryptoService.KeyPairBytes kp = pq.generateKemKeyPair();
    PqCryptoService.Encapsulation enc = pq.encapsulate(kp.publicKey(), CTX);
    byte[] other =
        pq.decapsulate(
            kp.privateKey(), enc.encapsulation(), "webhook_secret:wf_other".getBytes(StandardCharsets.UTF_8));
    assertThat(other).isNotEqualTo(enc.dataKey());
  }

  @ParameterizedTest
  @MethodSource("impls")
  void aeadRoundTripAndTamperDetection(PqCryptoService pq) {
    byte[] key = new byte[32];
    key[0] = 7;
    byte[] aad = "aad".getBytes(StandardCharsets.UTF_8);
    byte[] sealed = pq.aeadSeal(key, "whsec_triage_501".getBytes(StandardCharsets.UTF_8), aad);
    assertThat(new String(pq.aeadOpen(key, sealed, aad), StandardCharsets.UTF_8))
        .isEqualTo("whsec_triage_501");

    byte[] tampered = sealed.clone();
    tampered[tampered.length - 1] ^= 1;
    assertThatThrownBy(() -> pq.aeadOpen(key, tampered, aad)).isInstanceOf(CryptoException.class);
    assertThatThrownBy(() -> pq.aeadOpen(key, sealed, "other".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(CryptoException.class);
  }

  @ParameterizedTest
  @MethodSource("impls")
  void signaturesVerifyAndDetectTampering(PqCryptoService pq) {
    PqCryptoService.KeyPairBytes kp = pq.generateSigningKeyPair();
    byte[] payload = "{\"id\":\"wf_support_triage\"}".getBytes(StandardCharsets.UTF_8);
    byte[] sig = pq.sign(kp.privateKey(), payload);
    assertThat(pq.verify(kp.publicKey(), payload, sig)).isTrue();

    byte[] altered = "{\"id\":\"wf_support_triagE\"}".getBytes(StandardCharsets.UTF_8);
    assertThat(pq.verify(kp.publicKey(), altered, sig)).isFalse();
    assertThat(pq.verify(kp.publicKey(), payload, new byte[] {1, 2, 3})).isFalse();
    assertThat(pq.verify(null, payload, sig)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("impls")
  void signatureFromAnotherKeyDoesNotVerify(PqCryptoService pq) {
    PqCryptoService.KeyPairBytes a = pq.generateSigningKeyPair();
    PqCryptoService.KeyPairBytes b = pq.generateSigningKeyPair();
    byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
    assertThat(pq.verify(b.publicKey(), payload, pq.sign(a.privateKey(), payload))).isFalse();
  }
}
