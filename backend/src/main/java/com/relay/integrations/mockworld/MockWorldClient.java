package com.relay.integrations.mockworld;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.config.RelayProperties;
import com.relay.domain.error.ErrorCode;
import com.relay.integrations.http.OutboundHttp;
import com.relay.nodes.NodeFailureException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Anti-Corruption Layer over the mock world ({@code scripts/mock_world.py}). Translates engine
 * commands into the mock world's routes and JSON, and its responses and error envelopes back into
 * small engine-side records — no mock-world DTO leaks into the engine.
 *
 * <p>Every method is a side effect and <b>requires</b> an idempotency key: "every side-effect call
 * to the mock world ({@code notify}, {@code order_action}, mutating {@code http_request}) carries
 * an {@code Idempotency-Key} header" — API_CONTRACT, Engine Behavior.
 *
 * <p>4xx responses are business-rule rejections ({@link NodeFailureException}, not retried); 5xx,
 * 429 and timeouts surface from {@link OutboundHttp} as transient.
 */
@Component
public class MockWorldClient {

  private final OutboundHttp http;
  private final String baseUrl;

  /**
   * Creates the client.
   *
   * @param http outbound HTTP
   * @param props relay properties ({@code MOCK_WORLD_URL})
   */
  public MockWorldClient(OutboundHttp http, RelayProperties props) {
    this.http = http;
    String url = props.mockWorld().url();
    this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  /**
   * {@code POST /email/send}.
   *
   * @param to recipient
   * @param subject optional subject
   * @param message body
   * @param idempotencyKey key
   * @return delivery receipt
   */
  public Delivery sendEmail(String to, String subject, String message, String idempotencyKey) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("to", to);
    if (subject != null) {
      body.put("subject", subject);
    }
    body.put("message", message);
    return delivery("email.send", post("/email/send", body, idempotencyKey));
  }

  /**
   * {@code POST /chat/message}.
   *
   * @param channel channel, e.g. {@code #support}
   * @param message body
   * @param idempotencyKey key
   * @return delivery receipt
   */
  public Delivery postChat(String channel, String message, String idempotencyKey) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("channel", channel);
    body.put("message", message);
    return delivery("chat.message", post("/chat/message", body, idempotencyKey));
  }

  /**
   * {@code POST /orders/{id}/refund}. Omitting the amount refunds the order total; the mock world
   * rejects amounts above it.
   *
   * @param orderId order id (path-encoded — it may come from an untrusted payload)
   * @param amountUsd optional amount
   * @param idempotencyKey key
   * @return action result
   */
  public OrderActionResult refund(String orderId, BigDecimal amountUsd, String idempotencyKey) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    if (amountUsd != null) {
      body.put("amount_usd", amountUsd);
    }
    return orderAction(
        "order.refund", post("/orders/" + segment(orderId) + "/refund", body, idempotencyKey));
  }

  /**
   * {@code POST /orders/{id}/replacement}.
   *
   * @param orderId order id
   * @param idempotencyKey key
   * @return action result
   */
  public OrderActionResult replacement(String orderId, String idempotencyKey) {
    return orderAction(
        "order.replacement",
        post(
            "/orders/" + segment(orderId) + "/replacement",
            JsonNodeFactory.instance.objectNode(),
            idempotencyKey));
  }

  private OutboundHttp.Response post(String path, JsonNode body, String idempotencyKey) {
    Objects.requireNonNull(idempotencyKey, "side effects require an idempotency key");
    return http.send("POST", baseUrl + path, Map.of("Idempotency-Key", idempotencyKey), body);
  }

  private static Delivery delivery(String action, OutboundHttp.Response r) {
    requireOk(action, r);
    JsonNode j = r.json();
    return new Delivery(
        j != null && j.path("delivered").asBoolean(false),
        j == null ? null : j.path("notification_id").asText(null),
        r.replayed());
  }

  private static OrderActionResult orderAction(String action, OutboundHttp.Response r) {
    requireOk(action, r);
    JsonNode j = r.json();
    return new OrderActionResult(
        j == null ? null : j.path("status").asText(null),
        j == null ? null : j.path("reference_id").asText(null),
        r.replayed());
  }

  private static void requireOk(String action, OutboundHttp.Response r) {
    if (!r.ok()) {
      String msg = r.errorMessage();
      throw new NodeFailureException(
          ErrorCode.UPSTREAM_REJECTED,
          "mock world rejected " + action + " with " + r.status() + (msg == null ? "" : ": " + msg),
          Map.of("status", r.status(), "action", action));
    }
  }

  private static String segment(String orderId) {
    if (orderId == null || orderId.isBlank()) {
      throw new NodeFailureException(ErrorCode.INVALID_PARAM_VALUE, "order_id is empty");
    }
    // Encode so a payload-supplied id like "../admin/reset" cannot change the route.
    return URLEncoder.encode(orderId, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /**
   * Result of a notify call.
   *
   * @param delivered mock world's {@code delivered}
   * @param notificationId mock world's {@code notification_id}
   * @param replayed true if the world replayed an earlier call with the same key
   */
  public record Delivery(boolean delivered, String notificationId, boolean replayed) {}

  /**
   * Result of an order action.
   *
   * @param status e.g. {@code refunded}
   * @param referenceId e.g. {@code ref_…}
   * @param replayed true if replayed
   */
  public record OrderActionResult(String status, String referenceId, boolean replayed) {}
}
