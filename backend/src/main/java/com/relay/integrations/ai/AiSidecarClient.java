package com.relay.integrations.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.config.RelayProperties;
import com.relay.domain.error.ErrorCode;
import com.relay.nodes.NodeFailureException;
import com.relay.nodes.TransientNodeException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Adapter for the Python AI sidecar ({@code POST /classify}). Spring WebClient with a hard timeout
 * and the {@code aiSidecar} circuit breaker. Provider credentials live in the sidecar's environment
 * and never cross this boundary.
 *
 * <p>Errors: 5xx / 429 / timeouts / connection failures are transient (the engine retries with
 * backoff); other 4xx are permanent ({@code ai_provider_error}).
 */
@Component
public class AiSidecarClient implements AiModelPort {

  private final WebClient web;
  private final Duration timeout;
  private final CircuitBreaker breaker;

  /**
   * Creates the client.
   *
   * @param builder WebClient builder
   * @param props relay properties
   * @param breakers circuit breakers
   */
  public AiSidecarClient(
      WebClient.Builder builder, RelayProperties props, CircuitBreakerRegistry breakers) {
    this.web = builder.baseUrl(props.ai().sidecarUrl()).build();
    this.timeout = Duration.ofMillis(props.ai().timeoutMs());
    this.breaker = breakers.circuitBreaker("aiSidecar");
  }

  @Override
  public ModelReply complete(ModelRequest request) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("prompt", request.prompt());
    body.set("output_schema", request.outputSchema());
    if (request.repairFeedback() != null) {
      body.put("repair_feedback", request.repairFeedback());
    }
    ObjectNode meta = body.putObject("metadata");
    meta.put("run_id", request.runId());
    meta.put("node_id", request.nodeId());
    return breaker.executeSupplier(() -> call(body, request.traceId()));
  }

  private ModelReply call(ObjectNode body, String traceId) {
    JsonNode res;
    try {
      res =
          web.post()
              .uri("/classify")
              .contentType(MediaType.APPLICATION_JSON)
              .header("X-Trace-Id", traceId == null ? "" : traceId)
              .bodyValue(body)
              .retrieve()
              .bodyToMono(JsonNode.class)
              .timeout(timeout)
              .block();
    } catch (WebClientResponseException e) {
      int s = e.getStatusCode().value();
      if (s >= 500 || s == 429) {
        throw new TransientNodeException(
            ErrorCode.UPSTREAM_UNAVAILABLE, "AI sidecar returned " + s, e);
      }
      throw new NodeFailureException(
          ErrorCode.AI_PROVIDER_ERROR, "AI sidecar rejected the request with " + s);
    } catch (WebClientRequestException e) {
      throw new TransientNodeException(
          ErrorCode.UPSTREAM_UNAVAILABLE, "AI sidecar unreachable: " + e.getClass().getSimpleName(), e);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof TimeoutException || e instanceof IllegalStateException) {
        throw new TransientNodeException(
            ErrorCode.NODE_TIMEOUT, "AI sidecar timed out after " + timeout.toMillis() + " ms", e);
      }
      throw e;
    }
    if (res == null) {
      throw new TransientNodeException(ErrorCode.UPSTREAM_UNAVAILABLE, "AI sidecar returned no body");
    }
    JsonNode output = res.get("output");
    return new ModelReply(
        output == null || output.isNull() ? null : output,
        res.path("raw_text").asText(null),
        res.path("tokens_prompt").asInt(0),
        res.path("tokens_completion").asInt(0),
        res.path("provider").asText("unknown"),
        res.path("model").asText(null));
  }
}
