package com.relay.domain.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.catalog.ParamSpec;
import com.relay.domain.catalog.TriggerTypeSpec;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.schedule.CronSchedule;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;
import java.util.Optional;

/**
 * The trigger type must be in the catalog's {@code triggers} list and carry its required config
 * (webhook {@code secret}, schedule {@code cron} as a valid 5-field expression).
 */
public final class TriggerRule extends ValidationRule {

  @Override
  protected boolean check(ValidationContext ctx) {
    JsonNode trigger = ctx.definition().path("trigger");
    if (!trigger.isObject() || !trigger.path("type").isTextual()) {
      return true; // ShapeRule already reported it
    }
    String type = trigger.get("type").asText();
    Optional<TriggerTypeSpec> spec = ctx.catalog().trigger(type);
    if (spec.isEmpty()) {
      ctx.add(
          Violation.error(
              ErrorCode.INVALID_TRIGGER,
              "Trigger type '" + type + "' is not in the catalog " + ctx.catalog().triggerTypes(),
              null,
              "trigger.type"));
      return true;
    }
    for (ParamSpec p : spec.get().config().values()) {
      JsonNode v = trigger.get(p.name());
      boolean missing = v == null || v.isNull() || (v.isTextual() && v.asText().isBlank());
      if (p.required() && missing) {
        ctx.add(
            Violation.error(
                ErrorCode.INVALID_TRIGGER,
                capitalize(type) + " trigger requires '" + p.name() + "'",
                null,
                "trigger." + p.name()));
      }
    }
    if ("schedule".equals(type) && trigger.path("cron").isTextual()) {
      String cron = trigger.get("cron").asText();
      Optional<String> problem = CronSchedule.problem(cron);
      problem.ifPresent(
          msg ->
              ctx.add(
                  Violation.error(
                      ErrorCode.INVALID_TRIGGER,
                      "Schedule trigger cron '" + cron + "' is invalid: " + msg,
                      null,
                      "trigger.cron")));
    }
    return true;
  }

  private static String capitalize(String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
