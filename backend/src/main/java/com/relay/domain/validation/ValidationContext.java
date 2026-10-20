package com.relay.domain.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.catalog.NodeCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * State passed along the validation chain: the raw definition, the catalog, accumulated
 * violations, and lazily-built indexes that later rules reuse.
 */
public final class ValidationContext {

  private final JsonNode definition;
  private final NodeCatalog catalog;
  private final List<Violation> violations = new ArrayList<>();
  private Map<String, JsonNode> nodesById;

  /**
   * Creates a context.
   *
   * @param definition raw definition JSON (may be malformed — rules must be defensive)
   * @param catalog node catalog
   */
  public ValidationContext(JsonNode definition, NodeCatalog catalog) {
    this.definition = definition;
    this.catalog = catalog;
  }

  /** The raw definition. */
  public JsonNode definition() {
    return definition;
  }

  /** The node catalog. */
  public NodeCatalog catalog() {
    return catalog;
  }

  /** Records a finding. */
  public void add(Violation v) {
    violations.add(v);
  }

  /** Findings so far. */
  public List<Violation> violations() {
    return Collections.unmodifiableList(violations);
  }

  /** True if any blocking error has been recorded. */
  public boolean hasErrors() {
    return violations.stream().anyMatch(Violation::isError);
  }

  /** Node objects in declaration order (only objects with a textual id; first wins on dupes). */
  public Map<String, JsonNode> nodesById() {
    if (nodesById == null) {
      Map<String, JsonNode> map = new LinkedHashMap<>();
      for (JsonNode n : nodes()) {
        JsonNode id = n.get("id");
        if (id != null && id.isTextual()) {
          map.putIfAbsent(id.asText(), n);
        }
      }
      nodesById = Collections.unmodifiableMap(map);
    }
    return nodesById;
  }

  /** The {@code nodes} array elements that are objects. */
  public List<JsonNode> nodes() {
    List<JsonNode> out = new ArrayList<>();
    definition.path("nodes").forEach(n -> {
      if (n.isObject()) {
        out.add(n);
      }
    });
    return out;
  }

  /** Builds the final report. */
  public ValidationReport report() {
    return new ValidationReport(violations);
  }
}
