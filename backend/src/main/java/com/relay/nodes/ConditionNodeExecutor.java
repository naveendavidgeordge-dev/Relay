package com.relay.nodes;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.domain.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code condition}: "Evaluates a comparison and routes the run to on_true or on_false." —
 * node_catalog.json
 *
 * <p>Params (verbatim): {@code left} (templatable), {@code op} (enum {@code equals}, {@code
 * not_equals}, {@code greater_than}, {@code less_than}, {@code contains}), {@code right}
 * (templatable). Output: {@code {"result": boolean}}; the engine follows {@code on_true} / {@code
 * on_false} from it.
 *
 * <p>"greater_than/less_than compare numerically when both sides parse as numbers; otherwise the
 * comparison fails the step with a clear error." — catalog notes. {@code equals}, {@code
 * not_equals} and {@code contains} compare the resolved strings exactly.
 */
@Component
public class ConditionNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "condition";
  }

  @Override
  public NodeResult execute(NodeExecutionContext ctx) {
    String left = ctx.text("left", "");
    String op = ctx.text("op", "");
    String right = ctx.text("right", "");
    boolean result =
        switch (op) {
          case "equals" -> left.equals(right);
          case "not_equals" -> !left.equals(right);
          case "contains" -> left.contains(right);
          case "greater_than" -> compare(ctx, op, left, right) > 0;
          case "less_than" -> compare(ctx, op, left, right) < 0;
          default ->
              throw new NodeFailureException(
                  ErrorCode.INVALID_PARAM_VALUE, "unsupported condition op '" + op + "'");
        };
    ObjectNode out = JsonNodeFactory.instance.objectNode();
    out.put("result", result);
    ObjectNode detail = JsonNodeFactory.instance.objectNode();
    detail.put("compared_as", op.endsWith("_than") ? "number" : "string");
    detail.put("branch", result ? "on_true" : "on_false");
    return NodeResult.completed(out, List.of(detail));
  }

  private static int compare(NodeExecutionContext ctx, String op, String left, String right) {
    BigDecimal l = number(left);
    BigDecimal r = number(right);
    if (l == null || r == null) {
      throw new NodeFailureException(
          ErrorCode.CONDITION_NOT_NUMERIC,
          "Condition '" + ctx.nodeId() + "' " + op + " needs numbers on both sides; got left='"
              + clip(left) + "', right='" + clip(right) + "'",
          Map.of("left_numeric", l != null, "right_numeric", r != null));
    }
    return l.compareTo(r);
  }

  private static BigDecimal number(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(s.strip());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String clip(String s) {
    return s.length() <= 40 ? s : s.substring(0, 40) + "…";
  }
}
