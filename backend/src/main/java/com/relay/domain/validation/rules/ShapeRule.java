package com.relay.domain.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;
import java.util.regex.Pattern;

/**
 * Structural shape of the fixed definition contract: "{@code id}, {@code name}, {@code trigger},
 * {@code entry}, {@code limits}, and {@code nodes[]} with {@code next} (or {@code on_true}/{@code
 * on_false} on condition nodes)" — API_CONTRACT, Fixed Contract §1.
 *
 * <p>This is the only rule enforced at create/update time. {@code entry} and {@code limits} are
 * checked by their own publish-time rules so their errors carry distinct codes. Halts the chain if
 * the root is not an object or {@code nodes} is not an array.
 */
public final class ShapeRule extends ValidationRule {

  private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_\\-]{1,128}$");

  @Override
  protected boolean check(ValidationContext ctx) {
    JsonNode def = ctx.definition();
    if (def == null || !def.isObject()) {
      ctx.add(err("Definition must be a JSON object", null, null));
      return false;
    }
    JsonNode id = def.get("id");
    if (id != null && !(id.isTextual() && ID.matcher(id.asText()).matches())) {
      ctx.add(err("'id' must be 1-128 characters of letters, digits, '_' or '-'", null, "id"));
    }
    JsonNode name = def.get("name");
    if (name == null || !name.isTextual() || name.asText().isBlank()) {
      ctx.add(err("Definition is missing a non-empty 'name'", null, "name"));
    }
    JsonNode trigger = def.get("trigger");
    if (trigger == null || !trigger.isObject()) {
      ctx.add(err("Definition is missing the 'trigger' object", null, "trigger"));
    } else if (!trigger.path("type").isTextual()) {
      ctx.add(err("'trigger.type' must be a string", null, "trigger.type"));
    }
    JsonNode nodes = def.get("nodes");
    if (nodes == null || !nodes.isArray()) {
      ctx.add(err("Definition is missing the 'nodes' array", null, "nodes"));
      return false;
    }
    int index = 0;
    for (JsonNode n : nodes) {
      checkNode(ctx, n, index++);
    }
    return true;
  }

  private static void checkNode(ValidationContext ctx, JsonNode n, int index) {
    String where = "nodes[" + index + "]";
    if (!n.isObject()) {
      ctx.add(err(where + " must be an object", null, where));
      return;
    }
    JsonNode id = n.get("id");
    String nodeId = id != null && id.isTextual() ? id.asText() : null;
    if (nodeId == null || !ID.matcher(nodeId).matches()) {
      ctx.add(
          err(where + " needs an 'id' of letters, digits, '_' or '-'", nodeId, where + ".id"));
    }
    if (!n.path("type").isTextual() || n.path("type").asText().isBlank()) {
      ctx.add(err("Node '" + label(nodeId, where) + "' is missing 'type'", nodeId, "type"));
    }
    JsonNode params = n.get("params");
    if (params != null && !params.isNull() && !params.isObject()) {
      ctx.add(err("Node '" + label(nodeId, where) + "' 'params' must be an object", nodeId, "params"));
    }
    for (String edge : new String[] {"next", "on_true", "on_false"}) {
      JsonNode e = n.get(edge);
      if (e != null && !e.isNull() && !e.isTextual()) {
        ctx.add(
            err(
                "Node '" + label(nodeId, where) + "' '" + edge + "' must be a node id or null",
                nodeId,
                edge));
      }
    }
  }

  private static String label(String nodeId, String where) {
    return nodeId != null ? nodeId : where;
  }

  private static Violation err(String message, String nodeId, String field) {
    return Violation.error(ErrorCode.INVALID_DEFINITION, message, nodeId, field);
  }
}
