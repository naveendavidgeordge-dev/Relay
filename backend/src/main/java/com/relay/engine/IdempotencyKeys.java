package com.relay.engine;

/**
 * Idempotency-key derivation for side-effect steps.
 *
 * <p>"{@code {run_id}:{node_id}} is the canonical choice. Do not include the attempt number: a
 * retry must reuse the key, or replay detection cannot work." — API_CONTRACT, Engine Behavior.
 * "If a node legitimately executes twice because the run looped back to it, include the step
 * sequence — loop iterations are distinct effects; retries of one iteration are not." —
 * IMPLEMENTATION_GUIDE, FAQ.
 *
 * <p>The key is computed once when the step's intent row is written and then read back from the
 * database by every retry and resume, so it cannot drift.
 */
public final class IdempotencyKeys {

  private IdempotencyKeys() {}

  /**
   * Key for a step.
   *
   * @param runId run id
   * @param nodeId node id
   * @param sequence step sequence (only used for loop revisits)
   * @param priorExecutions how many times this node already executed earlier in the run
   * @return {@code run:node} on first execution, {@code run:node:s<sequence>} on revisits
   */
  public static String forStep(String runId, String nodeId, int sequence, long priorExecutions) {
    return priorExecutions == 0 ? runId + ":" + nodeId : runId + ":" + nodeId + ":s" + sequence;
  }
}
