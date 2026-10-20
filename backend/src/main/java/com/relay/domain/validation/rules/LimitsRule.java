package com.relay.domain.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;

/**
 * {@code limits.max_steps} must be a positive integer — the engine cannot enforce the step cap
 * without it ("a run that executes more than its {@code limits.max_steps} nodes ends {@code
 * failed}" — Case Study, Must Have §7). {@code timeout_seconds} and {@code max_ai_tokens} are
 * optional non-negative integers.
 */
public final class LimitsRule extends ValidationRule {

  @Override
  protected boolean check(ValidationContext ctx) {
    JsonNode limits = ctx.definition().get("limits");
    if (limits == null || !limits.isObject()) {
      ctx.add(err("Definition is missing the 'limits' object (limits.max_steps is required)",
          "limits"));
      return true;
    }
    JsonNode max = limits.get("max_steps");
    if (max == null || !max.canConvertToExactIntegral() || max.asInt() < 1) {
      ctx.add(err("'limits.max_steps' must be an integer >= 1", "limits.max_steps"));
    }
    for (String f : new String[] {"timeout_seconds", "max_ai_tokens"}) {
      JsonNode v = limits.get(f);
      if (v != null && !v.isNull() && (!v.canConvertToExactIntegral() || v.asLong() < 0)) {
        ctx.add(err("'limits." + f + "' must be an integer >= 0", "limits." + f));
      }
    }
    return true;
  }

  private static Violation err(String message, String field) {
    return Violation.error(ErrorCode.INVALID_LIMITS, message, null, field);
  }
}
