package com.relay.persistence.entity;

import com.relay.domain.run.RunEvent;
import com.relay.domain.run.RunStatus;
import com.relay.domain.run.TriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row of {@code runs}. Status changes go exclusively through {@link #apply(RunEvent, Instant)} so
 * the {@link RunStatus} transition table is the only way state moves.
 */
@Entity
@Table(name = "runs")
public class RunEntity {

  @Id
  @Column(name = "run_id", nullable = false, length = 64)
  private String runId;

  @Column(name = "workflow_id", nullable = false, length = 128)
  private String workflowId;

  @Column(name = "workflow_version", nullable = false)
  private int workflowVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "definition_snapshot_json", nullable = false, columnDefinition = "jsonb")
  private String definitionSnapshotJson;

  @Column(name = "snapshot_signature")
  private byte[] snapshotSignature;

  @Column(name = "snapshot_key_id", length = 64)
  private String snapshotKeyId;

  @Column(name = "status", nullable = false, length = 24)
  private String status;

  @Column(name = "trigger_type", nullable = false, length = 16)
  private String triggerType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "input_json", nullable = false, columnDefinition = "jsonb")
  private String inputJson;

  @Column(name = "current_node_id", length = 128)
  private String currentNodeId;

  @Column(name = "steps_executed", nullable = false)
  private int stepsExecuted;

  @Column(name = "ai_tokens_used", nullable = false)
  private int aiTokensUsed;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "error_json", columnDefinition = "jsonb")
  private String errorJson;

  @Column(name = "cancel_requested", nullable = false)
  private boolean cancelRequested;

  @Column(name = "cancelled_by", length = 128)
  private String cancelledBy;

  @Column(name = "trace_id", length = 64)
  private String traceId;

  @Column(name = "lease_owner", length = 128)
  private String leaseOwner;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected RunEntity() {}

  /**
   * Creates a queued run positioned at the definition's entry node.
   *
   * @param runId run id
   * @param workflowId workflow id
   * @param workflowVersion executed version
   * @param snapshotJson secret-free definition snapshot
   * @param snapshotSignature ML-DSA signature over the canonical snapshot
   * @param snapshotKeyId signing key id
   * @param triggerType how the run started
   * @param inputJson trigger body
   * @param entryNodeId first node
   * @param traceId trace id from the triggering request
   * @param now creation time
   */
  public RunEntity(
      String runId,
      String workflowId,
      int workflowVersion,
      String snapshotJson,
      byte[] snapshotSignature,
      String snapshotKeyId,
      TriggerType triggerType,
      String inputJson,
      String entryNodeId,
      String traceId,
      Instant now) {
    this.runId = runId;
    this.workflowId = workflowId;
    this.workflowVersion = workflowVersion;
    this.definitionSnapshotJson = snapshotJson;
    this.snapshotSignature = snapshotSignature == null ? null : snapshotSignature.clone();
    this.snapshotKeyId = snapshotKeyId;
    this.status = RunStatus.QUEUED.wire();
    this.triggerType = triggerType.wire();
    this.inputJson = inputJson;
    this.currentNodeId = entryNodeId;
    this.traceId = traceId;
    this.createdAt = now;
  }

  @PrePersist
  @PreUpdate
  void touch() {
    this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  /**
   * Applies a state-machine event.
   *
   * @param event the event
   * @param now event time (sets {@code started_at} / {@code finished_at})
   * @return the new status
   */
  public RunStatus apply(RunEvent event, Instant now) {
    RunStatus next = getStatus().on(event);
    this.status = next.wire();
    if (event == RunEvent.START && startedAt == null) {
      startedAt = now;
    }
    if (next.isTerminal()) {
      finishedAt = now;
      leaseOwner = null;
      leaseExpiresAt = null;
    }
    return next;
  }

  /** Increments the executed-step counter (loop iterations count — DATA_MODEL "Loop accounting"). */
  public int incrementStepsExecuted() {
    return ++stepsExecuted;
  }

  /** Adds AI token usage. */
  public void addAiTokens(int tokens) {
    this.aiTokensUsed += tokens;
  }

  public String getRunId() {
    return runId;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public int getWorkflowVersion() {
    return workflowVersion;
  }

  public String getDefinitionSnapshotJson() {
    return definitionSnapshotJson;
  }

  public byte[] getSnapshotSignature() {
    return snapshotSignature == null ? null : snapshotSignature.clone();
  }

  public String getSnapshotKeyId() {
    return snapshotKeyId;
  }

  public RunStatus getStatus() {
    return RunStatus.fromWire(status);
  }

  public TriggerType getTriggerType() {
    return TriggerType.fromWire(triggerType);
  }

  public String getInputJson() {
    return inputJson;
  }

  public String getCurrentNodeId() {
    return currentNodeId;
  }

  public void setCurrentNodeId(String currentNodeId) {
    this.currentNodeId = currentNodeId;
  }

  public int getStepsExecuted() {
    return stepsExecuted;
  }

  public int getAiTokensUsed() {
    return aiTokensUsed;
  }

  public String getErrorJson() {
    return errorJson;
  }

  public void setErrorJson(String errorJson) {
    this.errorJson = errorJson;
  }

  public boolean isCancelRequested() {
    return cancelRequested;
  }

  /**
   * Flags a cooperative cancellation (checked by the engine between steps).
   *
   * @param by actor
   */
  public void requestCancel(String by) {
    this.cancelRequested = true;
    this.cancelledBy = by;
  }

  public String getCancelledBy() {
    return cancelledBy;
  }

  public String getTraceId() {
    return traceId;
  }

  public String getLeaseOwner() {
    return leaseOwner;
  }

  public Instant getLeaseExpiresAt() {
    return leaseExpiresAt;
  }

  /**
   * Mirrors the queue lease onto the run for console visibility.
   *
   * @param owner worker id, or null
   * @param expiresAt lease expiry, or null
   */
  public void setLease(String owner, Instant expiresAt) {
    this.leaseOwner = owner;
    this.leaseExpiresAt = expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
