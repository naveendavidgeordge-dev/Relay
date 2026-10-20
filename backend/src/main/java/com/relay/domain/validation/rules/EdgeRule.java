package com.relay.domain.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.catalog.NodeTypeSpec;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;
import java.util.Map;
import java.util.Optional;

/**
 * Edges must point at existing nodes and use the right edge names.
 *
 * <ul>
 *   <li>{@code invalid_edge_target} — "a {@code next}/{@code on_true}/{@code on_false} pointing to
 *       a nonexistent node" (API_CONTRACT §1).
 *   <li>{@code invalid_edge} — a condition node using {@code next}, or a non-branching node using
 *       {@code on_true}/{@code on_false} ("the only node type with two outgoing edges" —
 *       node_catalog.json, condition).
 * </ul>
 *
 * <p>Backward edges and self-loops are legal: "Backward jumps (loops) are legal and must not be
 * rejected" (API_CONTRACT §1).
 */
public final class EdgeRule extends ValidationRule {

  private static final String[] EDGES = {"next", "on_true", "on_false"};

  @Override
  protected boolean check(ValidationContext ctx) {
    Map<String, JsonNode> byId = ctx.nodesById();
    for (JsonNode n : ctx.nodes()) {
      String id = n.path("id").asText("?");
      Optional<NodeTypeSpec> spec = ctx.catalog().node(n.path("type").asText(null));
      if (spec.isPresent()) {
        boolean branching = spec.get().isBranching();
        if (branching && hasEdge(n, "next")) {
          ctx.add(
              Violation.error(
                  ErrorCode.INVALID_EDGE,
                  "Node '" + id + "' (" + spec.get().type() + ") must route with on_true/on_false,"
                      + " not next",
                  id,
                  "next"));
        }
        if (!branching) {
          for (String e : new String[] {"on_true", "on_false"}) {
            if (hasEdge(n, e)) {
              ctx.add(
                  Violation.error(
                      ErrorCode.INVALID_EDGE,
                      "Node '" + id + "' (" + spec.get().type() + ") cannot use '" + e
                          + "'; only condition nodes branch",
                      id,
                      e));
            }
          }
        }
      }
      for (String edge : EDGES) {
        JsonNode target = n.get(edge);
        if (target != null && target.isTextual() && !byId.containsKey(target.asText())) {
          ctx.add(
              Violation.error(
                  ErrorCode.INVALID_EDGE_TARGET,
                  "Node '" + id + "' has " + edge + " -> '" + target.asText()
                      + "', but no node '" + target.asText() + "' exists",
                  id,
                  edge));
        }
      }
    }
    return true;
  }

  private static boolean hasEdge(JsonNode n, String edge) {
    JsonNode e = n.get(edge);
    return e != null && !e.isNull();
  }
}
