package com.relay.domain.event;

import java.time.Instant;

/**
 * Domain events emitted while runs execute (Observer pattern via Spring's {@code
 * ApplicationEventPublisher}). Listeners — structured trace logs, metrics, a future SSE live view
 * — react after the transaction commits, so they never observe a step that was rolled back.
 */
public sealed interface TraceEvent
    permits TraceEvent.RunStatusChanged, TraceEvent.StepStarted, TraceEvent.StepFinished {

  /** Run id the event belongs to. */
  String runId();

  /** When it happened. */
  Instant at();

  /**
   * A run moved between states.
   *
   * @param runId run id
   * @param workflowId workflow id
   * @param from previous status (wire form, null on creation)
   * @param to new status (wire form)
   * @param at time
   */
  record RunStatusChanged(String runId, String workflowId, String from, String to, Instant at)
      implements TraceEvent {}

  /**
   * A step's intent was persisted and the node is about to execute.
   *
   * @param runId run id
   * @param nodeId node id
   * @param nodeType node type
   * @param sequence step sequence
   * @param idempotencyKey key, if a side effect
   * @param at time
   */
  record StepStarted(
      String runId, String nodeId, String nodeType, int sequence, String idempotencyKey, Instant at)
      implements TraceEvent {}

  /**
   * A step reached a terminal or waiting status.
   *
   * @param runId run id
   * @param nodeId node id
   * @param nodeType node type
   * @param sequence step sequence
   * @param status step status (wire form)
   * @param attempts attempts used
   * @param durationMs duration
   * @param errorCode error code if failed
   * @param at time
   */
  record StepFinished(
      String runId,
      String nodeId,
      String nodeType,
      int sequence,
      String status,
      int attempts,
      Long durationMs,
      String errorCode,
      Instant at)
      implements TraceEvent {}
}
