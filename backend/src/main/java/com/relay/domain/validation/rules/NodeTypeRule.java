package com.relay.domain.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;

/**
 * Every node type must exist in the catalog ({@code invalid_node_type}).
 *
 * <p>"a node {@code type} not present in {@code data/node_catalog.json}" — API_CONTRACT §1. The
 * message format is the contract's own example: {@code Node 'n1' has unknown type 'teleport'}.
 */
public final class NodeTypeRule extends ValidationRule {

  @Override
  protected boolean check(ValidationContext ctx) {
    for (JsonNode n : ctx.nodes()) {
      JsonNode type = n.get("type");
      if (type == null || !type.isTextual()) {
        continue; // ShapeRule reported it
      }
      if (ctx.catalog().node(type.asText()).isEmpty()) {
        String id = n.path("id").asText("?");
        ctx.add(
            Violation.error(
                ErrorCode.INVALID_NODE_TYPE,
                "Node '" + id + "' has unknown type '" + type.asText() + "'",
                id,
                "type"));
      }
    }
    return true;
  }
}
