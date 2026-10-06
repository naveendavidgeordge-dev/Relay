package com.relay.persistence.entity;

import com.relay.domain.run.StepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row of {@code steps}: one node execution (attempt group). Created in status {@code running}
 * with its resolved input and idempotency key <em>before</em> the node runs (persist intent), and
 * completed with its output <em>before</em> the run advances (persist result).
 */
@Entity
@Table(name = "steps")
public class StepEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "run_id", nullable = false, length = 64)
  private String runId;

  @Column(name = "node_id", nullable = false, length = 128)
  private String nodeId;

  @Column(name = "node_type", nullable = false, length = 32)
  private String nodeType;

  @Column(name = "sequence", nullable = false)
  private int sequence;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  @Column(name = "attempt", nullable = false)
  private int attempt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "resolved_input_json", columnDefinition = "jsonb")
  private String resolvedInputJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "output_json", columnDefinition = "jsonb")
  private String outputJson;

  @Column(name = "tokens_prompt")
  private Integer tokensPrompt;

  @Column(name = "tokens_completion")
  private Integer tokensCompletion;

  @Column(name = "idempotency_key", length = 256)
  private String idempotencyKey;

  @Column(name = "resume_at")
  private Instant resumeAt;

  @Column(name = "approval_id", length = 64)
  private String approvalId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attempts_json", nullable = false, columnDefinition = "jsonb")
  private String attemptsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "error_json", columnDefinition = "jsonb")
  private String errorJson;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "duration_ms")
  private Long durationMs;

  protected StepEntity() {}

  /**
   * Creates an in-flight step (status {@code running}, attempt 1).
   *
   * @param runId run id
   * @param nodeId node id
   * @param nodeType node type
   * @param sequence execution order within the run (1-based)
   * @param resolvedInputJson params after template resolution
   * @param idempotencyKey key for side-effect nodes, else null
   * @param startedAt start time
   */
  public StepEntity(
      String runId,
      String nodeId,
      String nodeType,
      int sequence,
      String resolvedInputJson,
      String idempotencyKey,
      Instant startedAt) {
    this.runId = runId;
    this.nodeId = nodeId;
    this.nodeType = nodeType;
    this.sequence = sequence;
    this.status = StepStatus.RUNNING.wire();
    this.attempt = 1;
    this.resolvedInputJson = resolvedInputJson;
    this.idempotencyKey = idempotencyKey;
    this.attemptsJson = "[]";
    this.startedAt = startedAt;
  }

  /**
   * Marks the step finished in a terminal step status.
   *
   * @param newStatus succeeded / failed / cancelled
   * @param now finish time
   */
  public void finish(StepStatus newStatus, Instant now) {
    this.status = newStatus.wire();
    this.finishedAt = now;
    this.durationMs = Math.max(0, Duration.between(startedAt, now).toMillis());
  }

  /** Marks the step as waiting (approval decision or delay). */
  public void markWaiting() {
    this.status = StepStatus.WAITING.wire();
  }

  public Long getId() {
    return id;
  }

  public String getRunId() {
    return runId;
  }

  public String getNodeId() {
    return nodeId;
  }

  public String getNodeType() {
    return nodeType;
  }

  public int getSequence() {
    return sequence;
  }

  public StepStatus getStatus() {
    return StepStatus.fromWire(status);
  }

  public int getAttempt() {
    return attempt;
  }

  public void setAttempt(int attempt) {
    this.attempt = attempt;
  }

  public String getResolvedInputJson() {
    return resolvedInputJson;
  }

  public void setResolvedInputJson(String resolvedInputJson) {
    this.resolvedInputJson = resolvedInputJson;
  }

  public String getOutputJson() {
    return outputJson;
  }

  public void setOutputJson(String outputJson) {
    this.outputJson = outputJson;
  }

  public Integer getTokensPrompt() {
    return tokensPrompt;
  }

  public Integer getTokensCompletion() {
    return tokensCompletion;
  }

  /**
   * Records AI token usage for the step (summed over the repair attempt, if any).
   *
   * @param prompt prompt tokens
   * @param completion completion tokens
   */
  public void setTokens(Integer prompt, Integer completion) {
    this.tokensPrompt = prompt;
    this.tokensCompletion = completion;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Instant getResumeAt() {
    return resumeAt;
  }

  public void setResumeAt(Instant resumeAt) {
    this.resumeAt = resumeAt;
  }

  public String getApprovalId() {
    return approvalId;
  }

  public void setApprovalId(String approvalId) {
    this.approvalId = approvalId;
  }

  public String getAttemptsJson() {
    return attemptsJson;
  }

  public void setAttemptsJson(String attemptsJson) {
    this.attemptsJson = attemptsJson;
  }

  public String getErrorJson() {
    return errorJson;
  }

  public void setErrorJson(String errorJson) {
    this.errorJson = errorJson;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public Long getDurationMs() {
    return durationMs;
  }
}
