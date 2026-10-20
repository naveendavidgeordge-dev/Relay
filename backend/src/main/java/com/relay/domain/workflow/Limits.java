package com.relay.domain.workflow;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Run caps from {@code limits}. Only {@code max_steps} is Must Have; the others are carried for the
 * Good To Have caps (see DESIGN/00 conflict C8).
 *
 * @param maxSteps maximum node executions per run (loop iterations count)
 * @param timeoutSeconds wall-clock cap, or null
 * @param maxAiTokens AI token budget, or null; 0 means "no AI expected" and is not enforced
 */
public record Limits(int maxSteps, Integer timeoutSeconds, Integer maxAiTokens) {

  /**
   * Parses {@code limits} (assumed valid).
   *
   * @param json the limits object
   * @return the limits
   */
  public static Limits from(JsonNode json) {
    return new Limits(
        json.path("max_steps").asInt(),
        json.hasNonNull("timeout_seconds") ? json.get("timeout_seconds").asInt() : null,
        json.hasNonNull("max_ai_tokens") ? json.get("max_ai_tokens").asInt() : null);
  }

  /** Human-readable reason recorded when the step cap stops a run. */
  public String maxStepsReason() {
    return "max_steps cap (" + maxSteps + ") exceeded";
  }
}
