package com.relay.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic JSON serialization (object keys sorted recursively, no insignificant whitespace),
 * used as the byte string that ML-DSA signatures cover. Two semantically equal definitions always
 * produce the same bytes, so a signature survives a round-trip through {@code jsonb}.
 */
public final class CanonicalJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private CanonicalJson() {}

  /**
   * Canonical UTF-8 bytes of {@code node}.
   *
   * @param node any JSON value
   * @return canonical bytes
   */
  public static byte[] bytes(JsonNode node) {
    try {
      return MAPPER.writeValueAsString(sorted(node)).getBytes(StandardCharsets.UTF_8);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Canonical string of {@code node}.
   *
   * @param node any JSON value
   * @return canonical string
   */
  public static String string(JsonNode node) {
    return new String(bytes(node), StandardCharsets.UTF_8);
  }

  private static JsonNode sorted(JsonNode node) {
    if (node == null) {
      return JsonNodeFactory.instance.nullNode();
    }
    if (node.isObject()) {
      ObjectNode out = JsonNodeFactory.instance.objectNode();
      Map<String, JsonNode> fields = new TreeMap<>();
      node.properties().forEach(e -> fields.put(e.getKey(), e.getValue()));
      fields.forEach((k, v) -> out.set(k, sorted(v)));
      return out;
    }
    if (node.isArray()) {
      ArrayNode out = JsonNodeFactory.instance.arrayNode();
      node.forEach(v -> out.add(sorted(v)));
      return out;
    }
    return node;
  }
}
