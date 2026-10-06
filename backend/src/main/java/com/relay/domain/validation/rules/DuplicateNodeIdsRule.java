package com.relay.domain.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Node ids must be unique within a definition ({@code duplicate_node_ids}). */
public final class DuplicateNodeIdsRule extends ValidationRule {

  @Override
  protected boolean check(ValidationContext ctx) {
    Set<String> seen = new HashSet<>();
    Set<String> dupes = new LinkedHashSet<>();
    for (JsonNode n : ctx.nodes()) {
      JsonNode id = n.get("id");
      if (id != null && id.isTextual() && !seen.add(id.asText())) {
        dupes.add(id.asText());
      }
    }
    for (String d : dupes) {
      ctx.add(
          Violation.error(
              ErrorCode.DUPLICATE_NODE_IDS, "Node id '" + d + "' is used more than once", d, "id"));
    }
    return true;
  }
}
