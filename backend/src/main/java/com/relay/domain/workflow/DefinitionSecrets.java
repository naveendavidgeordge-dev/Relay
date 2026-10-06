package com.relay.domain.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

/**
 * Keeps webhook secrets out of stored definitions, snapshots and API responses.
 *
 * <p>"Secret hygiene: webhook secrets and any model API keys never appear in traces, console views,
 * or error messages." — EVALUATION_GUIDE, Additional Checks
 */
public final class DefinitionSecrets {

  /** Placeholder rendered instead of a secret. Submitting it back keeps the stored secret. */
  public static final String REDACTED = "********";

  private DefinitionSecrets() {}

  /**
   * Splits a submitted definition into a secret-free copy and the secret (if any).
   *
   * @param submitted definition as received
   * @return the stripped copy and the extracted secret
   */
  public static Stripped strip(JsonNode submitted) {
    JsonNode copy = submitted.deepCopy();
    String secret = null;
    if (copy.path("trigger").isObject()) {
      ObjectNode trigger = (ObjectNode) copy.get("trigger");
      JsonNode s = trigger.remove("secret");
      if (s != null && s.isTextual() && !REDACTED.equals(s.asText())) {
        secret = s.asText();
      }
    }
    return new Stripped(copy, Optional.ofNullable(secret));
  }

  /**
   * Returns a copy suitable for API responses: if the trigger is a webhook, its secret is rendered
   * as {@link #REDACTED} so the seed shape is preserved without leaking the value.
   *
   * @param stored a stripped definition
   * @return a redacted copy
   */
  public static JsonNode redactedView(JsonNode stored) {
    JsonNode copy = stored.deepCopy();
    if (copy.path("trigger").isObject() && "webhook".equals(copy.path("trigger").path("type").asText())) {
      ((ObjectNode) copy.get("trigger")).put("secret", REDACTED);
    }
    return copy;
  }

  /**
   * Re-inserts a placeholder secret so validation of a stored (stripped) definition can see that a
   * secret exists without handling the real value.
   *
   * @param stored stripped definition
   * @param hasSecret whether the vault holds a secret for this workflow
   * @return a copy for validation
   */
  public static JsonNode forValidation(JsonNode stored, boolean hasSecret) {
    JsonNode copy = stored.deepCopy();
    if (hasSecret && copy.path("trigger").isObject()) {
      ((ObjectNode) copy.get("trigger")).put("secret", REDACTED);
    }
    return copy;
  }

  /**
   * Result of {@link #strip(JsonNode)}.
   *
   * @param definition secret-free definition
   * @param secret extracted secret, if one was submitted
   */
  public record Stripped(JsonNode definition, Optional<String> secret) {}
}
