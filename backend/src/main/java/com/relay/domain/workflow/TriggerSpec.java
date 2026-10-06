package com.relay.domain.workflow;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The {@code trigger} block. The webhook secret is deliberately not part of this record: it is
 * stripped from stored definitions and lives in the secret vault (DESIGN/00 conflict C9).
 *
 * @param type {@code webhook}, {@code schedule} or {@code manual}
 * @param cron 5-field cron for schedule triggers, else null
 */
public record TriggerSpec(String type, String cron) {

  /**
   * Parses the trigger block (assumed valid).
   *
   * @param json trigger JSON
   * @return the trigger spec
   */
  public static TriggerSpec from(JsonNode json) {
    return new TriggerSpec(
        json.path("type").asText(), json.hasNonNull("cron") ? json.get("cron").asText() : null);
  }
}
