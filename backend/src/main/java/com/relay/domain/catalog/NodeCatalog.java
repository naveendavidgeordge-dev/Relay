package com.relay.domain.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The closed registry of node and trigger types ({@code data/node_catalog.json}). Publish
 * validation, the engine's side-effect and approval-gate decisions, and the NL compiler all read
 * from this one object, so nothing can disagree about what a type is.
 *
 * <p>"Published workflows may only use node types listed here, with the required params present.
 * The NL compiler must never emit a type outside this file." — node_catalog.json {@code _comment}
 */
public final class NodeCatalog {

  private final Map<String, NodeTypeSpec> nodes;
  private final Map<String, TriggerTypeSpec> triggers;
  private final JsonNode raw;

  private NodeCatalog(
      Map<String, NodeTypeSpec> nodes, Map<String, TriggerTypeSpec> triggers, JsonNode raw) {
    this.nodes = Collections.unmodifiableMap(nodes);
    this.triggers = Collections.unmodifiableMap(triggers);
    this.raw = raw.deepCopy();
  }

  /**
   * Parses the catalog JSON.
   *
   * @param root parsed {@code node_catalog.json}
   * @return the catalog
   * @throws IllegalArgumentException if the file does not have the expected shape
   */
  public static NodeCatalog parse(JsonNode root) {
    if (root == null || !root.path("nodes").isArray() || !root.path("triggers").isArray()) {
      throw new IllegalArgumentException("node catalog must have 'nodes' and 'triggers' arrays");
    }
    Map<String, NodeTypeSpec> nodes = new LinkedHashMap<>();
    for (JsonNode n : root.get("nodes")) {
      String type = requireText(n, "type");
      List<String> branches = new ArrayList<>();
      n.path("branches").forEach(b -> branches.add(b.asText()));
      nodes.put(
          type,
          new NodeTypeSpec(
              type,
              n.path("description").asText(null),
              n.path("side_effect").asBoolean(false),
              n.path("requires_approval").asBoolean(false),
              parseParams(n.path("params")),
              branches));
    }
    Map<String, TriggerTypeSpec> triggers = new LinkedHashMap<>();
    for (JsonNode t : root.get("triggers")) {
      String type = requireText(t, "type");
      triggers.put(
          type,
          new TriggerTypeSpec(
              type, t.path("description").asText(null), parseParams(t.path("config"))));
    }
    return new NodeCatalog(nodes, triggers, root);
  }

  private static Map<String, ParamSpec> parseParams(JsonNode params) {
    Map<String, ParamSpec> out = new LinkedHashMap<>();
    if (params == null || !params.isObject()) {
      return out;
    }
    for (Map.Entry<String, JsonNode> e : params.properties()) {
      JsonNode p = e.getValue();
      List<String> enumValues = new ArrayList<>();
      p.path("enum").forEach(v -> enumValues.add(v.asText()));
      out.put(
          e.getKey(),
          new ParamSpec(
              e.getKey(),
              p.path("type").asText("string"),
              p.path("required").asBoolean(false),
              enumValues,
              p.path("templatable").asBoolean(false),
              p.path("description").asText(null)));
    }
    return out;
  }

  private static String requireText(JsonNode node, String field) {
    JsonNode v = node.get(field);
    if (v == null || !v.isTextual() || v.asText().isBlank()) {
      throw new IllegalArgumentException("catalog entry missing '" + field + "'");
    }
    return v.asText();
  }

  /** Looks up a node type. */
  public Optional<NodeTypeSpec> node(String type) {
    return Optional.ofNullable(type == null ? null : nodes.get(type));
  }

  /** Looks up a trigger type. */
  public Optional<TriggerTypeSpec> trigger(String type) {
    return Optional.ofNullable(type == null ? null : triggers.get(type));
  }

  /** Every node type name, in catalog order. */
  public Set<String> nodeTypes() {
    return nodes.keySet();
  }

  /** Every trigger type name, in catalog order. */
  public Set<String> triggerTypes() {
    return triggers.keySet();
  }

  /** All node specs, in catalog order. */
  public List<NodeTypeSpec> nodeSpecs() {
    return List.copyOf(nodes.values());
  }

  /** True if the type exists and is marked {@code requires_approval}. */
  public boolean requiresApproval(String type) {
    return node(type).map(NodeTypeSpec::requiresApproval).orElse(false);
  }

  /** True if the type exists and is marked {@code side_effect}. */
  public boolean isSideEffect(String type) {
    return node(type).map(NodeTypeSpec::sideEffect).orElse(false);
  }

  /** A defensive copy of the raw catalog JSON (served by {@code GET /catalog}). */
  public JsonNode raw() {
    return raw.deepCopy();
  }
}
