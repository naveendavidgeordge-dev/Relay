package com.relay.domain.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;

/** "an {@code entry} pointing to a nonexistent node" — API_CONTRACT §1 ({@code invalid_entry}). */
public final class EntryRule extends ValidationRule {

  @Override
  protected boolean check(ValidationContext ctx) {
    JsonNode entry = ctx.definition().get("entry");
    if (entry == null || !entry.isTextual() || entry.asText().isBlank()) {
      ctx.add(
          Violation.error(
              ErrorCode.INVALID_ENTRY, "Definition is missing 'entry'", null, "entry"));
    } else if (!ctx.nodesById().containsKey(entry.asText())) {
      ctx.add(
          Violation.error(
              ErrorCode.INVALID_ENTRY,
              "Entry '" + entry.asText() + "' does not match any node",
              null,
              "entry"));
    }
    return true;
  }
}
