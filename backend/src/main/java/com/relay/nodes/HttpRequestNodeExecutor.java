package com.relay.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.relay.domain.error.ErrorCode;
import com.relay.integrations.http.OutboundHttp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * {@code http_request}: "Calls an HTTP endpoint. Mutating requests (non-GET) are side effects and
 * must carry an Idempotency-Key header." — node_catalog.json
 *
 * <p>Params (verbatim from the catalog): {@code method} (required, enum GET/POST/PUT/DELETE),
 * {@code url} (required, templatable), {@code headers} (object, templatable), {@code body} (object,
 * templatable). Output: {@code {"status": number, "body": object|string}}.
 *
 * <p>Statuses below 500 (except 429) complete the step so a workflow can branch on {@code status}
 * (DESIGN/00 conflict C10); 5xx/429/timeouts are transient and retried with the same key.
 */
@Component
public class HttpRequestNodeExecutor implements NodeExecutor {

  /** Catalog enum for {@code method}. */
  static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE");

  /** Headers a definition may not set: the engine owns them. */
  private static final Set<String> RESERVED_HEADERS =
      Set.of("idempotency-key", "host", "content-length", "connection", "transfer-encoding",
          "upgrade", "expect");

  private final OutboundHttp http;

  /**
   * Creates the executor.
   *
   * @param http outbound HTTP
   */
  public HttpRequestNodeExecutor(OutboundHttp http) {
    this.http = http;
  }

  @Override
  public String type() {
    return "http_request";
  }

  @Override
  public NodeResult execute(NodeExecutionContext ctx) {
    String method = ctx.text("method", "").toUpperCase(Locale.ROOT);
    if (!METHODS.contains(method)) {
      throw new NodeFailureException(
          ErrorCode.INVALID_PARAM_VALUE, "method must be one of " + METHODS + ", got '" + method + "'");
    }
    String url = ctx.text("url", "");
    Map<String, String> headers = new LinkedHashMap<>();
    JsonNode h = ctx.param("headers");
    if (h.isObject()) {
      h.properties()
          .forEach(
              e -> {
                if (!RESERVED_HEADERS.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                  headers.put(e.getKey(), e.getValue().asText());
                }
              });
    }
    boolean mutating = !"GET".equals(method);
    if (mutating) {
      if (ctx.idempotencyKey() == null) {
        throw new NodeFailureException(
            ErrorCode.INTERNAL_ERROR, "mutating http_request has no idempotency key");
      }
      headers.put("Idempotency-Key", ctx.idempotencyKey());
    }
    JsonNode body = ctx.param("body");

    OutboundHttp.Response res =
        http.send(method, url, headers, body.isMissingNode() ? null : body);

    ObjectNode out = JsonNodeFactory.instance.objectNode();
    out.put("status", res.status());
    out.set("body", res.json() != null ? res.json() : TextNode.valueOf(res.rawBody() == null ? "" : res.rawBody()));

    List<ObjectNode> details = new ArrayList<>();
    if (res.effectiveUrl() != null || res.replayed()) {
      ObjectNode d = JsonNodeFactory.instance.objectNode();
      d.put("attempt", ctx.attempt());
      if (res.effectiveUrl() != null) {
        d.put("effective_url", res.effectiveUrl());
      }
      if (res.replayed()) {
        d.put("replayed", true);
      }
      details.add(d);
    }
    return NodeResult.completed(out, details);
  }
}
