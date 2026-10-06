package com.relay.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.domain.catalog.NodeCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Loads the canonical fixtures from the test classpath. They are the {@code data/} files copied by
 * the Gradle {@code processResources} task — tests never use hand-edited copies.
 */
public final class Fixtures {

  /** Shared plain mapper for tests. */
  public static final ObjectMapper MAPPER = new ObjectMapper();

  private static NodeCatalog catalog;
  private static JsonNode seeds;

  private Fixtures() {}

  /** The real node catalog. */
  public static synchronized NodeCatalog catalog() {
    if (catalog == null) {
      catalog = NodeCatalog.parse(read("catalog/node_catalog.json"));
    }
    return catalog;
  }

  /** A seed workflow by id, as a mutable deep copy. */
  public static synchronized ObjectNode seed(String id) {
    if (seeds == null) {
      seeds = read("seed/seed_workflows.json");
    }
    for (JsonNode w : seeds.get("workflows")) {
      if (id.equals(w.path("id").asText())) {
        return ((ObjectNode) w).deepCopy();
      }
    }
    throw new IllegalArgumentException("no seed " + id);
  }

  /** Every seed workflow id. */
  public static String[] seedIds() {
    return new String[] {
      "wf_support_triage", "wf_expense_approval", "wf_slow_fulfillment", "wf_runaway"
    };
  }

  /** Parses a JSON literal (tests use text blocks). */
  public static ObjectNode json(String json) {
    try {
      return (ObjectNode) MAPPER.readTree(json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static JsonNode read(String path) {
    try (InputStream in = Fixtures.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException(path + " not on classpath — run via Gradle");
      }
      return MAPPER.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
