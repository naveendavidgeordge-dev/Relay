package com.relay.domain.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A parsed workflow definition in the fixed seed shape ({@code id}, {@code name}, {@code trigger},
 * {@code entry}, {@code limits}, {@code nodes[]}). Construct only from JSON that passed publish
 * validation — this type does not re-validate.
 *
 * @param id workflow id
 * @param name display name
 * @param description optional description
 * @param trigger trigger spec
 * @param entry id of the first node
 * @param limits run caps
 * @param nodes nodes in declaration order
 */
public record WorkflowDefinition(
    String id,
    String name,
    String description,
    TriggerSpec trigger,
    String entry,
    Limits limits,
    List<NodeDefinition> nodes) {

  /** Canonical constructor; copies the node list. */
  public WorkflowDefinition {
    nodes = List.copyOf(nodes);
  }

  /**
   * Parses a validated definition.
   *
   * @param json definition JSON
   * @return the definition
   */
  public static WorkflowDefinition from(JsonNode json) {
    List<NodeDefinition> nodes = new ArrayList<>();
    json.path("nodes").forEach(n -> nodes.add(NodeDefinition.from(n)));
    return new WorkflowDefinition(
        json.path("id").asText(),
        json.path("name").asText(),
        json.hasNonNull("description") ? json.get("description").asText() : null,
        TriggerSpec.from(json.path("trigger")),
        json.path("entry").asText(),
        Limits.from(json.path("limits")),
        nodes);
  }

  /** Looks up a node by id. */
  public Optional<NodeDefinition> node(String nodeId) {
    return nodes.stream().filter(n -> n.id().equals(nodeId)).findFirst();
  }

  /** Nodes keyed by id, in declaration order. */
  public Map<String, NodeDefinition> nodesById() {
    Map<String, NodeDefinition> map = new LinkedHashMap<>();
    nodes.forEach(n -> map.put(n.id(), n));
    return Collections.unmodifiableMap(map);
  }
}
