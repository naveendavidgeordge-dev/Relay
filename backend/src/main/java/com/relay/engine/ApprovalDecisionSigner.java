package com.relay.engine;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.persistence.entity.ApprovalEntity;
import com.relay.security.crypto.PayloadSigner;
import com.relay.support.CanonicalJson;
import org.springframework.stereotype.Component;

/**
 * Signs and verifies approval decisions with ML-DSA-65. The signed record binds the decision to
 * its run, node and step sequence, so a decision cannot be replayed onto another run or edited in
 * place without breaking the gate.
 */
@Component
public class ApprovalDecisionSigner implements ApprovalGate.ApprovalSignatures {

  private final PayloadSigner signer;

  /**
   * Creates the signer.
   *
   * @param signer payload signer
   */
  public ApprovalDecisionSigner(PayloadSigner signer) {
    this.signer = signer;
  }

  /**
   * Signs a decided approval in place.
   *
   * @param approval decided approval
   */
  public void sign(ApprovalEntity approval) {
    PayloadSigner.Signature sig = signer.sign(payload(approval));
    approval.sign(sig.bytes(), sig.keyId());
  }

  @Override
  public boolean verify(ApprovalEntity approval) {
    return signer.verify(
        payload(approval), approval.getDecisionKeyId(), approval.getDecisionSignature());
  }

  private static byte[] payload(ApprovalEntity a) {
    ObjectNode n = JsonNodeFactory.instance.objectNode();
    n.put("approval_id", a.getId());
    n.put("run_id", a.getRunId());
    n.put("node_id", a.getNodeId());
    n.put("step_sequence", a.getStepSequence());
    n.put("status", a.getStatus().wire());
    n.put("decided_by", a.getDecidedBy());
    n.put("decided_at", a.getDecidedAt() == null ? null : a.getDecidedAt().toString());
    return CanonicalJson.bytes(n);
  }
}
