package com.relay.application.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.domain.catalog.NodeCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic post-processing applied to every compiler proposal, whatever produced it.
 *
 * <ul>
 *   <li><b>Catalog boundary:</b> "The compiler must never emit node types outside the catalog."
 *       A proposal containing one is turned into a refusal — never repaired by guessing.
 *   <li><b>Approval before sensitive actions:</b> "If the description implies a sensitive action
 *       ({@code order_action}), the generated workflow must place an {@code approval} node before
 *       it." (API_CONTRACT §6). Every {@code requires_approval} node reachable without an approval
 *       gets one inserted in front of it, and the result says so.
 * </ul>
 */
@Component
public class CompilerGuardrails {

  private final NodeCatalog catalog;

  /**
   * Creates the guardrails.
   *
   * @param catalog node catalog
   */
  public CompilerGuardrails(NodeCatalog catalog) {
    this.catalog = catalog;
  }

  /**
   * Node types in a definition that the catalog does not define.
   *
   * @param definition candidate definition
   * @return invented types (empty when clean)
   */
  public Set<String> inventedTypes(JsonNode definition) {
    Set<String> out = new HashSet<>();
    definition.path("nodes").forEach(n -> {
      String t = n.path("type").asText(null);
      if (t == null || catalog.node(t).isEmpty()) {
        out.add(String.valueOf(t));
      }
    });
    return out;
  }

  /**
   * Inserts approval gates in front of ungated sensitive nodes, in place.
   *
   * @param def definition (mutated)
   * @return human notes describing each insertion
   */
  public List<String> ensureApprovalGates(ObjectNode def) {
    List<String> notes = new ArrayList<>();
    if (!def.path("nodes").isArray()) {
      return notes;
    }
    ArrayNode nodes = (ArrayNode) def.get("nodes");
    for (JsonNode n : new ArrayList<JsonNode>(toList(nodes))) {
      String type = n.path("type").asText();
      String id = n.path("id").asText();
      if (!catalog.requiresApproval(type) || !reachableWithoutApproval(def, id)) {
        continue;
      }
      String gateId = uniqueId(nodes, "approve_" + id);
      ObjectNode gate = nodes.addObject();
      gate.put("id", gateId);
      gate.put("type", "approval");
      gate.putObject("params").put(
          "message",
          "Approval required before '" + id + "' (" + type + ") for order {{trigger.body.order_id}}");
      gate.put("next", id);
      // Re-point every edge into the sensitive node (and the entry) at the gate.
      for (JsonNode other : nodes) {
        if (other == gate) {
          continue;
        }
        for (String e : new String[] {"next", "on_true", "on_false"}) {
          if (id.equals(other.path(e).asText(null))) {
            ((ObjectNode) other).put(e, gateId);
          }
        }
      }
      if (id.equals(def.path("entry").asText())) {
        def.put("entry", gateId);
      }
      notes.add(
          "Inserted approval node '" + gateId + "' before '" + id + "': " + type
              + " is requires_approval in the catalog, so a human must approve it even though the"
              + " description did not ask for one.");
    }
    return notes;
  }

  private boolean reachableWithoutApproval(ObjectNode def, String target) {
    Set<String> seen = new HashSet<>();
    List<String> queue = new ArrayList<>(List.of(def.path("entry").asText()));
    while (!queue.isEmpty()) {
      String id = queue.remove(0);
      if (!seen.add(id)) {
        continue;
      }
      if (id.equals(target)) {
        return true;
      }
      JsonNode n = byId(def, id);
      if (n == null || "approval".equals(n.path("type").asText())) {
        continue;
      }
      for (String e : new String[] {"next", "on_true", "on_false"}) {
        if (n.hasNonNull(e)) {
          queue.add(n.get(e).asText());
        }
      }
    }
    return false;
  }

  private static JsonNode byId(JsonNode def, String id) {
    for (JsonNode n : def.path("nodes")) {
      if (id.equals(n.path("id").asText())) {
        return n;
      }
    }
    return null;
  }

  private static String uniqueId(ArrayNode nodes, String base) {
    Set<String> ids = new HashSet<>();
    nodes.forEach(n -> ids.add(n.path("id").asText()));
    String id = base;
    for (int i = 2; ids.contains(id); i++) {
      id = base + "_" + i;
    }
    return id;
  }

  private static List<JsonNode> toList(ArrayNode a) {
    List<JsonNode> out = new ArrayList<>();
    a.forEach(out::add);
    return out;
  }
}
