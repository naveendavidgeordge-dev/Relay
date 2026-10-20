package com.relay.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;

/**
 * Everything an executor may know about the step it runs. Built with {@link #builder()}.
 *
 * @param runId run id
 * @param workflowId workflow id
 * @param nodeId node id
 * @param nodeType node type
 * @param sequence step sequence within the run
 * @param attempt attempt number (1-based; increments on retries and resumes)
 * @param params resolved params
 * @param idempotencyKey key for side-effect calls, or null
 * @param stepStartedAt persisted start time of the step (stable across resumes)
 * @param traceId trace id
 */
public record NodeExecutionContext(
    String runId,
    String workflowId,
    String nodeId,
    String nodeType,
    int sequence,
    int attempt,
    JsonNode params,
    String idempotencyKey,
    Instant stepStartedAt,
    String traceId) {

  /** Canonical constructor; validates required fields. */
  public NodeExecutionContext {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(nodeType, "nodeType");
    Objects.requireNonNull(params, "params");
    Objects.requireNonNull(stepStartedAt, "stepStartedAt");
  }

  /** A param by name (missing node if absent). */
  public JsonNode param(String name) {
    return params.path(name);
  }

  /** A text param, or {@code fallback} when absent/null. */
  public String text(String name, String fallback) {
    JsonNode v = params.get(name);
    return v == null || v.isNull() ? fallback : v.asText();
  }

  /** A copy with a different attempt number. */
  public NodeExecutionContext withAttempt(int newAttempt) {
    return new NodeExecutionContext(
        runId,
        workflowId,
        nodeId,
        nodeType,
        sequence,
        newAttempt,
        params,
        idempotencyKey,
        stepStartedAt,
        traceId);
  }

  /** A new builder. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link NodeExecutionContext}. */
  public static final class Builder {
    private String runId;
    private String workflowId;
    private String nodeId;
    private String nodeType;
    private int sequence = 1;
    private int attempt = 1;
    private JsonNode params;
    private String idempotencyKey;
    private Instant stepStartedAt;
    private String traceId;

    private Builder() {}

    /** Sets the run id. */
    public Builder runId(String v) {
      this.runId = v;
      return this;
    }

    /** Sets the workflow id. */
    public Builder workflowId(String v) {
      this.workflowId = v;
      return this;
    }

    /** Sets the node id. */
    public Builder nodeId(String v) {
      this.nodeId = v;
      return this;
    }

    /** Sets the node type. */
    public Builder nodeType(String v) {
      this.nodeType = v;
      return this;
    }

    /** Sets the sequence. */
    public Builder sequence(int v) {
      this.sequence = v;
      return this;
    }

    /** Sets the attempt. */
    public Builder attempt(int v) {
      this.attempt = v;
      return this;
    }

    /** Sets resolved params. */
    public Builder params(JsonNode v) {
      this.params = v;
      return this;
    }

    /** Sets the idempotency key. */
    public Builder idempotencyKey(String v) {
      this.idempotencyKey = v;
      return this;
    }

    /** Sets the step start time. */
    public Builder stepStartedAt(Instant v) {
      this.stepStartedAt = v;
      return this;
    }

    /** Sets the trace id. */
    public Builder traceId(String v) {
      this.traceId = v;
      return this;
    }

    /** Builds the context. */
    public NodeExecutionContext build() {
      return new NodeExecutionContext(
          runId,
          workflowId,
          nodeId,
          nodeType,
          sequence,
          attempt,
          params,
          idempotencyKey,
          stepStartedAt,
          traceId);
    }
  }
}
