package com.relay.domain.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.catalog.NodeTypeSpec;
import com.relay.domain.catalog.ParamSpec;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.template.TemplateRef;
import com.relay.domain.template.TemplateSyntax;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;
import java.util.Optional;

/**
 * Templates in templatable params must follow the path-lookup grammar and reference nodes that
 * exist ({@code invalid_template}). Catching {@code {{nodes.clasify.output.x}}} at publish is far
 * better than an {@code unresolvable} failure in the middle of a run.
 *
 * <p>Whether the referenced node has actually executed on a given path is only knowable at run
 * time; the resolver fails the step with a clear error in that case.
 */
public final class TemplateRule extends ValidationRule {

  @Override
  protected boolean check(ValidationContext ctx) {
    for (JsonNode n : ctx.nodes()) {
      Optional<NodeTypeSpec> spec = ctx.catalog().node(n.path("type").asText(null));
      if (spec.isEmpty()) {
        continue;
      }
      String id = n.path("id").asText("?");
      for (ParamSpec p : spec.get().params().values()) {
        JsonNode v = n.path("params").get(p.name());
        if (v != null && p.templatable()) {
          scan(ctx, id, "params." + p.name(), v);
        }
      }
    }
    return true;
  }

  private void scan(ValidationContext ctx, String nodeId, String field, JsonNode v) {
    if (v.isTextual()) {
      for (String expr : TemplateSyntax.expressions(v.asText())) {
        checkExpression(ctx, nodeId, field, expr);
      }
    } else if (v.isObject()) {
      v.properties().forEach(e -> scan(ctx, nodeId, field + "." + e.getKey(), e.getValue()));
    } else if (v.isArray()) {
      int i = 0;
      for (JsonNode item : v) {
        scan(ctx, nodeId, field + "[" + i++ + "]", item);
      }
    }
  }

  private static void checkExpression(
      ValidationContext ctx, String nodeId, String field, String expr) {
    if (TemplateSyntax.UNBALANCED.equals(expr)) {
      ctx.add(
          Violation.error(
              ErrorCode.INVALID_TEMPLATE,
              "Node '" + nodeId + "' " + field + " has an unclosed '{{' template",
              nodeId,
              field));
      return;
    }
    Optional<TemplateRef> ref = TemplateSyntax.parse(expr);
    if (ref.isEmpty()) {
      ctx.add(
          Violation.error(
              ErrorCode.INVALID_TEMPLATE,
              "Node '" + nodeId + "' " + field + " has an invalid template '{{" + expr
                  + "}}'; only {{trigger.body.<path>}} and {{nodes.<id>.output.<path>}} are"
                  + " supported",
              nodeId,
              field));
      return;
    }
    TemplateRef r = ref.get();
    if (r.root() == TemplateRef.Root.NODE_OUTPUT && !ctx.nodesById().containsKey(r.nodeId())) {
      ctx.add(
          Violation.error(
              ErrorCode.INVALID_TEMPLATE,
              "Node '" + nodeId + "' " + field + " references '{{" + expr + "}}', but no node '"
                  + r.nodeId() + "' exists",
              nodeId,
              field));
    }
  }
}
