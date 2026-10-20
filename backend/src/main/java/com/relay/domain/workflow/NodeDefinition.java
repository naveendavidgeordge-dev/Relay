package com.relay.domain.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One node of a definition in the fixed seed shape: {@code id}, {@code type}, {@code params}, and
 * either {@code next} or {@code on_true}/{@code on_false}. A {@code null} or absent edge ends the
 * run on that path.
 *
 * @param id node id, unique within the definition
 * @param type catalog node type
 * @param params raw params (templates unresolved)
 * @param next successor for non-branching nodes
 * @param onTrue successor when a condition is true
 * @param onFalse successor when a condition is false
 */
public record NodeDefinition(
    String id, String type, ObjectNode params, String next, String onTrue, String onFalse) {

  /** Canonical constructor; params default to an empty object and are copied. */
  public NodeDefinition {
    params = params == null ? JsonNodeFactory.instance.objectNode() : params.deepCopy();
  }

  /** A defensive copy of the params. */
  @Override
  public ObjectNode params() {
    return params.deepCopy();
  }

  /** A single param, or a missing node. */
  public JsonNode param(String name) {
    return params.path(name);
  }

  /** Non-null successors in declaration order ({@code next}, {@code on_true}, {@code on_false}). */
  public List<String> successors() {
    List<String> out = new ArrayList<>(2);
    if (next != null) {
      out.add(next);
    }
    if (onTrue != null) {
      out.add(onTrue);
    }
    if (onFalse != null) {
      out.add(onFalse);
    }
    return Collections.unmodifiableList(out);
  }

  /** True if some path out of this node ends the run (a null edge). */
  public boolean canTerminate(boolean branching) {
    return branching ? onTrue == null || onFalse == null : next == null;
  }

  /**
   * Parses a node object (assumed shape-valid).
   *
   * @param json node JSON
   * @return the node
   */
  public static NodeDefinition from(JsonNode json) {
    return new NodeDefinition(
        json.path("id").asText(),
        json.path("type").asText(),
        json.path("params").isObject() ? (ObjectNode) json.get("params") : null,
        textOrNull(json.get("next")),
        textOrNull(json.get("on_true")),
        textOrNull(json.get("on_false")));
  }

  private static String textOrNull(JsonNode n) {
    return n == null || n.isNull() ? null : n.asText();
  }
}
