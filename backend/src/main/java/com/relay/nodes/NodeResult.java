package com.relay.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;

/** What a node execution produced. Failures are exceptions, not results. */
public sealed interface NodeResult
    permits NodeResult.Completed, NodeResult.AwaitApproval, NodeResult.SleepUntil {

  /**
   * The node finished; the run advances along {@code next} (or {@code on_true}/{@code on_false}
   * by {@code output.result} for conditions).
   *
   * @param output persisted step output
   * @param tokens AI token usage, or null
   * @param details extra trace details (e.g. AI repair attempts, effective URL), may be empty
   */
  record Completed(JsonNode output, TokenUsage tokens, List<ObjectNode> details)
      implements NodeResult {
    /** Canonical constructor; copies details. */
    public Completed {
      details = details == null ? List.of() : List.copyOf(details);
    }
  }

  /**
   * The run must pause in {@code waiting_approval} with a pending approval carrying {@code
   * message}.
   *
   * @param message resolved approval message
   */
  record AwaitApproval(String message) implements NodeResult {}

  /**
   * The run must not continue before {@code until}; the worker releases the job instead of
   * sleeping ("A {@code resume_at} timestamp the worker polls is the more durable design" —
   * IMPLEMENTATION_GUIDE, FAQ).
   *
   * @param until resume time
   */
  record SleepUntil(Instant until) implements NodeResult {}

  /** A completed result without tokens or details. */
  static Completed completed(JsonNode output) {
    return new Completed(output, null, List.of());
  }

  /** A completed result with details. */
  static Completed completed(JsonNode output, List<ObjectNode> details) {
    return new Completed(output, null, details);
  }

  /** An approval pause. */
  static AwaitApproval awaitApproval(String message) {
    return new AwaitApproval(message);
  }

  /** A durable sleep. */
  static SleepUntil sleepUntil(Instant until) {
    return new SleepUntil(until);
  }
}
