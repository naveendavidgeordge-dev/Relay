package com.relay.domain.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.catalog.NodeTypeSpec;
import com.relay.domain.catalog.ParamSpec;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.template.TemplateSyntax;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;
import java.util.Map;
import java.util.Optional;

/**
 * Params are checked against the catalog entry of the node's type:
 *
 * <ul>
 *   <li>{@code missing_required_param} — "a missing required param (per the catalog)"
 *       (API_CONTRACT §1). A blank string counts as missing.
 *   <li>{@code invalid_param_value} — wrong JSON type, or a value outside the catalog {@code enum}.
 *   <li>{@code unknown_param} (warning) — a param the catalog does not define.
 * </ul>
 */
public final class ParamsRule extends ValidationRule {

  @Override
  protected boolean check(ValidationContext ctx) {
    for (JsonNode n : ctx.nodes()) {
      Optional<NodeTypeSpec> spec = ctx.catalog().node(n.path("type").asText(null));
      if (spec.isEmpty()) {
        continue; // NodeTypeRule reported it
      }
      String id = n.path("id").asText("?");
      JsonNode params = n.path("params");
      for (ParamSpec p : spec.get().params().values()) {
        checkParam(ctx, id, spec.get().type(), p, params.get(p.name()));
      }
      if (params.isObject()) {
        for (Map.Entry<String, JsonNode> e : params.properties()) {
          if (!spec.get().params().containsKey(e.getKey())) {
            ctx.add(
                Violation.warning(
                    ErrorCode.UNKNOWN_PARAM,
                    "Node '"
                        + id
                        + "' ("
                        + spec.get().type()
                        + ") has param '"
                        + e.getKey()
                        + "' which the catalog does not define; it will be ignored",
                    id,
                    "params." + e.getKey()));
          }
        }
      }
    }
    return true;
  }

  private static void checkParam(
      ValidationContext ctx, String id, String type, ParamSpec p, JsonNode v) {
    String field = "params." + p.name();
    boolean missing = v == null || v.isNull() || (v.isTextual() && v.asText().isBlank());
    if (missing) {
      if (p.required()) {
        ctx.add(
            Violation.error(
                ErrorCode.MISSING_REQUIRED_PARAM,
                "Node '" + id + "' (" + type + ") is missing required param '" + p.name() + "'",
                id,
                field));
      }
      return;
    }
    String problem = typeProblem(p, v);
    if (problem != null) {
      ctx.add(
          Violation.error(
              ErrorCode.INVALID_PARAM_VALUE,
              "Node '" + id + "' (" + type + ") param '" + p.name() + "' " + problem,
              id,
              field));
      return;
    }
    if (p.hasEnum() && !p.enumValues().contains(v.asText())) {
      ctx.add(
          Violation.error(
              ErrorCode.INVALID_PARAM_VALUE,
              "Node '"
                  + id
                  + "' ("
                  + type
                  + ") param '"
                  + p.name()
                  + "' is '"
                  + v.asText()
                  + "'; allowed: "
                  + p.enumValues(),
              id,
              field));
    }
    if ("delay".equals(type) && "seconds".equals(p.name()) && v.asDouble() < 0) {
      ctx.add(
          Violation.error(
              ErrorCode.INVALID_PARAM_VALUE,
              "Node '" + id + "' (delay) param 'seconds' must be >= 0",
              id,
              field));
    }
    if ("order_action".equals(type) && "amount_usd".equals(p.name()) && v.asDouble() <= 0) {
      ctx.add(
          Violation.error(
              ErrorCode.INVALID_PARAM_VALUE,
              "Node '" + id + "' (order_action) param 'amount_usd' must be > 0",
              id,
              field));
    }
  }

  /** Returns a problem description, or null when the JSON type matches the catalog type. */
  private static String typeProblem(ParamSpec p, JsonNode v) {
    return switch (p.type()) {
      case "number" -> v.isNumber() ? null : "must be a number";
      case "object" -> v.isObject() ? null : "must be an object";
      case "string" -> {
        if (v.isTextual()) {
          if (!p.templatable() && TemplateSyntax.containsTemplate(v.asText()) && !p.hasEnum()) {
            yield "does not support templates";
          }
          yield null;
        }
        // numbers / booleans are coerced to strings (e.g. condition right: 100)
        yield v.isValueNode() ? null : "must be a string";
      }
      default -> null;
    };
  }
}
