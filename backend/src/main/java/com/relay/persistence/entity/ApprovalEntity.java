package com.relay.persistence.entity;

import com.relay.domain.run.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Row of {@code approvals}. Only the approvals API can move a row out of {@code pending}; nothing
 * a model outputs can create or decide one ("Approval gate as data" — DATA_MODEL).
 */
@Entity
@Table(name = "approvals")
public class ApprovalEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "run_id", nullable = false, length = 64)
  private String runId;

  @Column(name = "node_id", nullable = false, length = 128)
  private String nodeId;

  @Column(name = "step_sequence", nullable = false)
  private int stepSequence;

  @Column(name = "message", nullable = false, columnDefinition = "text")
  private String message;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  @Column(name = "decided_by", length = 128)
  private String decidedBy;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decision_comment", columnDefinition = "text")
  private String decisionComment;

  @Column(name = "decision_signature")
  private byte[] decisionSignature;

  @Column(name = "decision_key_id", length = 64)
  private String decisionKeyId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ApprovalEntity() {}

  /**
   * Creates a pending approval.
   *
   * @param id approval id
   * @param runId run id
   * @param nodeId approval node id
   * @param stepSequence sequence of the approval step
   * @param message resolved message shown to the approver
   * @param createdAt creation time
   */
  public ApprovalEntity(
      String id, String runId, String nodeId, int stepSequence, String message, Instant createdAt) {
    this.id = id;
    this.runId = runId;
    this.nodeId = nodeId;
    this.stepSequence = stepSequence;
    this.message = message;
    this.status = ApprovalStatus.PENDING.wire();
    this.createdAt = createdAt;
  }

  /**
   * Records a decision.
   *
   * @param decision approved / rejected / cancelled
   * @param by decider
   * @param at decision time
   * @param comment optional comment
   */
  public void decide(ApprovalStatus decision, String by, Instant at, String comment) {
    this.status = decision.wire();
    this.decidedBy = by;
    this.decidedAt = at;
    this.decisionComment = comment;
  }

  /**
   * Attaches the ML-DSA signature over the decision.
   *
   * @param signature signature bytes
   * @param keyId signing key id
   */
  public void sign(byte[] signature, String keyId) {
    this.decisionSignature = signature == null ? null : signature.clone();
    this.decisionKeyId = keyId;
  }

  public String getId() {
    return id;
  }

  public String getRunId() {
    return runId;
  }

  public String getNodeId() {
    return nodeId;
  }

  public int getStepSequence() {
    return stepSequence;
  }

  public String getMessage() {
    return message;
  }

  public ApprovalStatus getStatus() {
    return ApprovalStatus.fromWire(status);
  }

  public String getDecidedBy() {
    return decidedBy;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public String getDecisionComment() {
    return decisionComment;
  }

  public byte[] getDecisionSignature() {
    return decisionSignature == null ? null : decisionSignature.clone();
  }

  public String getDecisionKeyId() {
    return decisionKeyId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
