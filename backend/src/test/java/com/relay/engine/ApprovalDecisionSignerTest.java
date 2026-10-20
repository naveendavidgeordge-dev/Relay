package com.relay.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.relay.domain.run.ApprovalStatus;
import com.relay.persistence.entity.ApprovalEntity;
import com.relay.security.crypto.PayloadSigner;
import com.relay.security.crypto.TestPqCryptoService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * Regression: an approval signed with a nanosecond timestamp must still verify after Postgres
 * stores it at microsecond precision — otherwise the gate rejects a genuine human approval.
 */
class ApprovalDecisionSignerTest {

  /** HMAC signer over the deterministic test crypto. */
  private static final class FakeSigner implements PayloadSigner {
    private final TestPqCryptoService pq = new TestPqCryptoService();
    private final byte[] key = pq.generateSigningKeyPair().privateKey();

    @Override
    public Signature sign(byte[] payload) {
      return new Signature("sig_test", "TEST", pq.sign(key, payload));
    }

    @Override
    public boolean verify(byte[] payload, String keyId, byte[] signature) {
      return pq.verify(key, payload, signature);
    }
  }

  private static ApprovalEntity decided(Instant at) {
    ApprovalEntity a = new ApprovalEntity("apr_1", "run_1", "gate", 3, "ok?", Instant.EPOCH);
    a.decide(ApprovalStatus.APPROVED, "alice", at, null);
    return a;
  }

  @Test
  void signatureSurvivesMicrosecondTruncationOnReload() {
    ApprovalDecisionSigner signer = new ApprovalDecisionSigner(new FakeSigner());
    Instant withNanos = Instant.parse("2026-10-08T17:25:14.123456789Z");
    ApprovalEntity signed = decided(withNanos);
    signer.sign(signed);

    // what the gate sees after the row round-trips through Postgres
    ApprovalEntity reloaded = decided(withNanos.truncatedTo(ChronoUnit.MICROS));
    reloaded.sign(signed.getDecisionSignature(), signed.getDecisionKeyId());

    assertThat(signer.verify(reloaded)).isTrue();
  }

  @Test
  void anEditedDecisionDoesNotVerify() {
    ApprovalDecisionSigner signer = new ApprovalDecisionSigner(new FakeSigner());
    ApprovalEntity signed = decided(Instant.parse("2026-10-08T17:25:14Z"));
    signer.sign(signed);

    ApprovalEntity forged = new ApprovalEntity("apr_1", "run_1", "gate", 3, "ok?", Instant.EPOCH);
    forged.decide(ApprovalStatus.APPROVED, "mallory", Instant.parse("2026-10-08T17:25:14Z"), null);
    forged.sign(signed.getDecisionSignature(), signed.getDecisionKeyId());

    assertThat(signer.verify(forged)).isFalse();
  }
}
