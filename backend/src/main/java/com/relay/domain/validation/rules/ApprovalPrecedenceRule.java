package com.relay.domain.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Warns when a {@code requires_approval} node can be reached from {@code entry} without passing an
 * {@code approval} node. This is advisory only: "Publishing may warn or reject; either way the
 * engine must refuse to execute the {@code order_action} at runtime." — EVALUATION_GUIDE,
 * Additional Checks. The runtime gate ({@code engine.ApprovalGate}) is the real enforcement.
 */
public final class ApprovalPrecedenceRule extends ValidationRule {

  @Override
  protected boolean check(ValidationContext ctx) {
    Map<String, JsonNode> byId = ctx.nodesById();
    String entry = ctx.definition().path("entry").asText(null);
    if (entry == null || !byId.containsKey(entry)) {
      return true;
    }
    // Walk from entry without expanding through approval nodes.
    Set<String> seen = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>();
    queue.add(entry);
    while (!queue.isEmpty()) {
      String id = queue.poll();
      if (!seen.add(id)) {
        continue;
      }
      JsonNode n = byId.get(id);
      String type = n.path("type").asText(null);
      if ("approval".equals(type)) {
        continue;
      }
      if (ctx.catalog().requiresApproval(type)) {
        ctx.add(
            Violation.warning(
                ErrorCode.APPROVAL_NOT_GUARANTEED,
                "Node '" + id + "' (" + type + ") requires approval but is reachable without"
                    + " passing an approval node; the engine will refuse to execute it at runtime",
                id,
                null));
      }
      for (String edge : new String[] {"next", "on_true", "on_false"}) {
        JsonNode t = n.get(edge);
        if (t != null && t.isTextual() && byId.containsKey(t.asText())) {
          queue.add(t.asText());
        }
      }
    }
    return true;
  }
}
