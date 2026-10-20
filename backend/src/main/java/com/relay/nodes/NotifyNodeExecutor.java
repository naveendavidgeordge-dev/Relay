package com.relay.nodes;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.domain.error.ErrorCode;
import com.relay.integrations.mockworld.MockWorldClient;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@code notify}: "Sends a message via the mock world: channel 'email' -> POST
 * {MOCK_WORLD_URL}/email/send, channel 'chat' -> POST {MOCK_WORLD_URL}/chat/message. Side effect:
 * must carry an Idempotency-Key." — node_catalog.json
 *
 * <p>Params (verbatim): {@code channel} (enum {@code email}, {@code chat}), {@code to}
 * (templatable; "email address, or chat channel name like #support"), {@code subject} (templatable,
 * "email only"), {@code message} (templatable). Output: {@code {"delivered": boolean,
 * "notification_id": string}}.
 *
 * <p>Targets come only from the definition's params: nothing an AI node outputs can redirect a
 * notification unless the workflow author templated {@code to} from that output
 * ({@code pay_inject_002}).
 */
@Component
public class NotifyNodeExecutor implements NodeExecutor {

  private final MockWorldClient world;

  /**
   * Creates the executor.
   *
   * @param world mock world client
   */
  public NotifyNodeExecutor(MockWorldClient world) {
    this.world = world;
  }

  @Override
  public String type() {
    return "notify";
  }

  @Override
  public NodeResult execute(NodeExecutionContext ctx) {
    if (ctx.idempotencyKey() == null) {
      throw new NodeFailureException(ErrorCode.INTERNAL_ERROR, "notify has no idempotency key");
    }
    String channel = ctx.text("channel", "");
    String to = ctx.text("to", "");
    String message = ctx.text("message", "");
    MockWorldClient.Delivery d =
        switch (channel) {
          case "email" ->
              world.sendEmail(to, ctx.text("subject", null), message, ctx.idempotencyKey());
          case "chat" -> world.postChat(to, message, ctx.idempotencyKey());
          default ->
              throw new NodeFailureException(
                  ErrorCode.INVALID_PARAM_VALUE, "channel must be 'email' or 'chat'");
        };
    ObjectNode out = JsonNodeFactory.instance.objectNode();
    out.put("delivered", d.delivered());
    out.put("notification_id", d.notificationId());
    if (!d.replayed()) {
      return NodeResult.completed(out);
    }
    ObjectNode detail = JsonNodeFactory.instance.objectNode();
    detail.put("attempt", ctx.attempt());
    detail.put("replayed", true);
    detail.put("note", "mock world absorbed a replay of idempotency key " + ctx.idempotencyKey());
    return NodeResult.completed(out, List.of(detail));
  }
}
