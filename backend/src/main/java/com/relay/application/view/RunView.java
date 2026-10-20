package com.relay.application.view;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/**
 * A run and its trace ({@code GET /runs/{runId}}).
 *
 * <p>Fixed contract: "returns at least {@code status} and {@code steps[]}, each step with a {@code
 * node_id} and {@code status}" (API_CONTRACT §2). "The trace must show resolved input, output,
 * attempts, timing, and (for {@code ai} steps) token usage" (§3). List responses omit {@code
 * steps}, {@code input}, {@code definition_snapshot} and {@code approvals}.
 *
 * @param runId run id
 * @param workflowId workflow id
 * @param workflowName workflow name (from the snapshot)
 * @param workflowVersion executed version
 * @param status one of the six contract statuses
 * @param triggerType webhook / manual / schedule
 * @param input trigger body ({@code {{trigger.body}}})
 * @param currentNodeId node the run is at (or will run next)
 * @param stepsExecuted node executions so far (loop iterations count)
 * @param maxSteps the cap from the snapshot
 * @param aiTokensUsed total AI tokens
 * @param error failure / cancellation record ({@code code}, {@code message}, {@code details})
 * @param failureReason {@code error.message}, e.g. {@code max_steps cap (12) exceeded}
 * @param cancelRequested cooperative cancel pending
 * @param createdAt trigger time
 * @param startedAt first picked up by a worker
 * @param finishedAt terminal time
 * @param durationMs finished - created, or elapsed so far
 * @param traceId trace id
 * @param leaseOwner worker currently holding the run
 * @param signatureValid whether the snapshot's ML-DSA signature verifies
 * @param signatureKeyId snapshot signing key id
 * @param definitionSnapshot the executed definition (detail only)
 * @param approvals approvals of this run (detail only)
 * @param steps the trace in sequence order (detail only)
 */
public record RunView(
    String runId,
    String workflowId,
    String workflowName,
    int workflowVersion,
    String status,
    String triggerType,
    JsonNode input,
    String currentNodeId,
    int stepsExecuted,
    Integer maxSteps,
    int aiTokensUsed,
    JsonNode error,
    String failureReason,
    boolean cancelRequested,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt,
    Long durationMs,
    String traceId,
    String leaseOwner,
    Boolean signatureValid,
    String signatureKeyId,
    JsonNode definitionSnapshot,
    List<ApprovalView> approvals,
    List<StepView> steps) {

  /**
   * One step of the trace.
   *
   * @param sequence execution order (node ids repeat in loops; sequence does not)
   * @param nodeId node id
   * @param type node type
   * @param status running / succeeded / failed / waiting / cancelled
   * @param attempt final attempt number
   * @param input resolved input (params after template resolution)
   * @param output persisted output
   * @param error error record if failed
   * @param startedAt start time
   * @param finishedAt end time
   * @param durationMs duration
   * @param tokensPrompt AI prompt tokens
   * @param tokensCompletion AI completion tokens
   * @param idempotencyKey key sent with the side effect
   * @param approvalId approval created by (approval node) or gating (sensitive node) this step
   * @param resumeAt delay resume time
   * @param sideEffect catalog {@code side_effect}
   * @param requiresApproval catalog {@code requires_approval}
   * @param attempts per-attempt log (retries, replays, AI repair)
   */
  public record StepView(
      int sequence,
      String nodeId,
      String type,
      String status,
      int attempt,
      JsonNode input,
      JsonNode output,
      JsonNode error,
      Instant startedAt,
      Instant finishedAt,
      Long durationMs,
      Integer tokensPrompt,
      Integer tokensCompletion,
      String idempotencyKey,
      String approvalId,
      Instant resumeAt,
      boolean sideEffect,
      boolean requiresApproval,
      JsonNode attempts) {}
}
