package com.relay.engine.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.relay.domain.catalog.NodeTypeSpec;
import com.relay.domain.catalog.ParamSpec;
import com.relay.domain.template.TemplateRef;
import com.relay.domain.template.TemplateSyntax;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;

/**
 * Resolves {@code {{trigger.body.…}}} and {@code {{nodes.<id>.output.…}}} in a node's templatable
 * params.
 *
 * <p>"Path lookups … interpolated into strings. No expressions, no filters, no arithmetic. An
 * unresolvable path should fail the step with a clear error, not silently render an empty string."
 * — IMPLEMENTATION_GUIDE, FAQ.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>Only params the catalog marks {@code templatable} are resolved; others pass through.
 *   <li>For {@code string} params the result is always text (numbers/booleans rendered, objects as
 *       compact JSON, JSON {@code null} as {@code null}).
 *   <li>For {@code object} params (headers, body) a value that is exactly one template keeps the
 *       referenced JSON type; mixed strings are interpolated.
 *   <li>Single pass: text that came <em>from</em> a payload is never re-scanned, so an untrusted
 *       body containing {@code {{…}}} cannot inject a lookup.
 *   <li>A missing path throws {@link TemplateResolutionException} — never an empty string.
 * </ul>
 */
public final class TemplateResolver {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Resolves a node's params.
   *
   * @param spec catalog spec of the node type
   * @param params raw params from the definition snapshot
   * @param scope lookup sources
   * @return resolved params (a new object)
   * @throws TemplateResolutionException if any reference cannot be resolved
   */
  public ObjectNode resolve(NodeTypeSpec spec, JsonNode params, Scope scope) {
    ObjectNode out = JsonNodeFactory.instance.objectNode();
    if (params == null || !params.isObject()) {
      return out;
    }
    for (Map.Entry<String, JsonNode> e : params.properties()) {
      ParamSpec p = spec.params().get(e.getKey());
      String field = "params." + e.getKey();
      if (p == null || !p.templatable()) {
        out.set(e.getKey(), e.getValue().deepCopy());
      } else if ("string".equals(p.type())) {
        out.set(e.getKey(), resolveText(e.getValue(), scope, field));
      } else {
        out.set(e.getKey(), resolveStructured(e.getValue(), scope, field));
      }
    }
    return out;
  }

  private JsonNode resolveText(JsonNode value, Scope scope, String field) {
    if (!value.isTextual()) {
      return value.isValueNode() ? TextNode.valueOf(value.asText()) : value.deepCopy();
    }
    return TextNode.valueOf(interpolate(value.asText(), scope, field));
  }

  private JsonNode resolveStructured(JsonNode value, Scope scope, String field) {
    if (value.isTextual()) {
      String s = value.asText();
      if (TemplateSyntax.isWholeTemplate(s)) {
        Matcher m = TemplateSyntax.TEMPLATE.matcher(s.strip());
        if (m.matches()) {
          return lookup(m.group(1), scope, field).deepCopy();
        }
      }
      return TextNode.valueOf(interpolate(s, scope, field));
    }
    if (value.isObject()) {
      ObjectNode out = JsonNodeFactory.instance.objectNode();
      value
          .properties()
          .forEach(
              e -> out.set(e.getKey(), resolveStructured(e.getValue(), scope, field + "." + e.getKey())));
      return out;
    }
    if (value.isArray()) {
      ArrayNode out = JsonNodeFactory.instance.arrayNode();
      int i = 0;
      for (JsonNode item : value) {
        out.add(resolveStructured(item, scope, field + "[" + i++ + "]"));
      }
      return out;
    }
    return value.deepCopy();
  }

  /**
   * Interpolates every template in {@code text}.
   *
   * @param text template text
   * @param scope lookup sources
   * @param field param path for error messages
   * @return rendered text
   */
  public String interpolate(String text, Scope scope, String field) {
    if (!TemplateSyntax.containsTemplate(text)) {
      return text;
    }
    Matcher m = TemplateSyntax.TEMPLATE.matcher(text);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(render(lookup(m.group(1), scope, field))));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private JsonNode lookup(String expression, Scope scope, String field) {
    TemplateRef ref =
        TemplateSyntax.parse(expression)
            .orElseThrow(
                () ->
                    new TemplateResolutionException(
                        "Template '{{" + expression + "}}' in " + field
                            + " is not a supported path lookup",
                        expression,
                        field));
    JsonNode current;
    String walked;
    if (ref.root() == TemplateRef.Root.TRIGGER_BODY) {
      current = scope.triggerBody();
      walked = "trigger.body";
    } else {
      Optional<JsonNode> out = scope.nodeOutput().apply(ref.nodeId());
      if (out.isEmpty()) {
        throw new TemplateResolutionException(
            "Template '{{" + expression + "}}' in " + field + " could not be resolved: node '"
                + ref.nodeId() + "' has not produced output in this run",
            expression,
            field);
      }
      current = out.get();
      walked = "nodes." + ref.nodeId() + ".output";
    }
    for (String seg : ref.path()) {
      JsonNode next = step(current, seg);
      if (next == null) {
        throw new TemplateResolutionException(
            "Template '{{" + expression + "}}' in " + field + " could not be resolved: " + walked
                + " has no field '" + seg + "'",
            expression,
            field);
      }
      current = next;
      walked = walked + "." + seg;
    }
    return current;
  }

  private static JsonNode step(JsonNode node, String seg) {
    if (node == null) {
      return null;
    }
    if (node.isObject()) {
      return node.get(seg);
    }
    if (node.isArray() && seg.chars().allMatch(Character::isDigit)) {
      int idx = Integer.parseInt(seg);
      return idx < node.size() ? node.get(idx) : null;
    }
    return null;
  }

  private static String render(JsonNode v) {
    if (v.isTextual()) {
      return v.asText();
    }
    if (v.isValueNode()) {
      return v.asText(); // numbers, booleans, null -> "null"
    }
    try {
      return MAPPER.writeValueAsString(v);
    } catch (JsonProcessingException e) {
      return v.toString();
    }
  }

  /**
   * Lookup sources for one step.
   *
   * @param triggerBody the run's input ({@code {{trigger.body}}})
   * @param nodeOutput latest succeeded output of a node in this run
   */
  public record Scope(JsonNode triggerBody, Function<String, Optional<JsonNode>> nodeOutput) {}
}
