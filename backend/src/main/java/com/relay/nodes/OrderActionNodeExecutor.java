package com.relay.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.domain.error.ErrorCode;
import com.relay.integrations.mockworld.MockWorldClient;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@code order_action}: "Executes a sensitive commerce action on the mock world: refund -> POST
 * {MOCK_WORLD_URL}/orders/{order_id}/refund, replacement -> POST
 * {MOCK_WORLD_URL}/orders/{order_id}/replacement. SENSITIVE: the engine must refuse to execute this
 * node unless an approval node was approved earlier in the same run. Side effect: must carry an
 * Idempotency-Key." — node_catalog.json
 *
 * <p>Params (verbatim): {@code action} (enum {@code refund}, {@code replacement}), {@code order_id}
 * (templatable), {@code amount_usd} (number, optional; "refund only; omitted means full refund").
 * Output: {@code {"status": string, "reference_id": string}}.
 *
 * <p>The approval gate is enforced <b>before</b> this executor is invoked, in {@code RunExecutor}
 * via {@code ApprovalGate} — a database query. This class never decides whether it is allowed to
 * run. Note that {@code amount_usd} is not templatable, so no payload or AI output can set it.
 */
@Component
public class OrderActionNodeExecutor implements NodeExecutor {

  private final MockWorldClient world;

  /**
   * Creates the executor.
   *
   * @param world mock world client
   */
  public OrderActionNodeExecutor(MockWorldClient world) {
    this.world = world;
  }

  @Override
  public String type() {
    return "order_action";
  }

  @Override
  public NodeResult execute(NodeExecutionContext ctx) {
    if (ctx.idempotencyKey() == null) {
      throw new NodeFailureException(ErrorCode.INTERNAL_ERROR, "order_action has no idempotency key");
    }
    String orderId = ctx.text("order_id", "");
    JsonNode amount = ctx.param("amount_usd");
    MockWorldClient.OrderActionResult r =
        switch (ctx.text("action", "")) {
          case "refund" ->
              world.refund(
                  orderId, amount.isNumber() ? amount.decimalValue() : null, ctx.idempotencyKey());
          case "replacement" -> world.replacement(orderId, ctx.idempotencyKey());
          default ->
              throw new NodeFailureException(
                  ErrorCode.INVALID_PARAM_VALUE, "action must be 'refund' or 'replacement'");
        };
    ObjectNode out = JsonNodeFactory.instance.objectNode();
    out.put("status", r.status());
    out.put("reference_id", r.referenceId());
    if (!r.replayed()) {
      return NodeResult.completed(out);
    }
    ObjectNode d = JsonNodeFactory.instance.objectNode();
    d.put("attempt", ctx.attempt());
    d.put("replayed", true);
    return NodeResult.completed(out, List.of(d));
  }
}
