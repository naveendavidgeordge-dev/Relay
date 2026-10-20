package com.relay.integrations.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.config.RelayProperties;
import com.relay.domain.error.ErrorCode;
import com.relay.nodes.NodeFailureException;
import com.relay.nodes.TransientNodeException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The one outbound HTTP path for node executors.
 *
 * <ul>
 *   <li><b>Timeouts on every call</b> — connect and response timeouts from {@code relay.http}: "a
 *       hung dependency must fail the step, not the engine" (Case Study, Technical Requirements).
 *   <li><b>Circuit breaker</b> — {@code mockWorld} for the mock world, {@code http-<host>} for
 *       anything else. 5xx/429/timeouts count as failures; an open circuit is retryable.
 *   <li><b>Classification</b> — 5xx, 429, timeouts and connection errors throw {@link
 *       TransientNodeException}; other statuses are returned to the caller to interpret.
 *   <li><b>URL aliases</b> and an optional <b>host allowlist</b> (SSRF guard for templated URLs).
 * </ul>
 */
@Component
public class OutboundHttp {

  private final HttpClient client;
  private final Duration readTimeout;
  private final UrlAliases aliases;
  private final Set<String> allowedHosts;
  private final String mockWorldPrefix;
  private final CircuitBreakerRegistry breakers;
  private final ObjectMapper mapper;

  /**
   * Creates the client.
   *
   * @param props relay properties
   * @param breakers circuit breaker registry
   * @param mapper Jackson mapper
   */
  public OutboundHttp(
      RelayProperties props, CircuitBreakerRegistry breakers, ObjectMapper mapper) {
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(props.http().connectTimeoutMs()))
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    this.readTimeout = Duration.ofMillis(props.http().readTimeoutMs());
    this.aliases = UrlAliases.parse(props.http().urlAliases());
    this.allowedHosts =
        props.http().allowedHosts() == null || props.http().allowedHosts().isBlank()
            ? Set.of()
            : Arrays.stream(props.http().allowedHosts().split(","))
                .map(s -> s.strip().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    this.mockWorldPrefix = stripSlash(props.mockWorld().url());
    this.breakers = breakers;
    this.mapper = mapper;
  }

  /**
   * Sends a request.
   *
   * @param method HTTP method
   * @param url URL as written in the definition (aliases applied here)
   * @param headers request headers
   * @param body JSON body or null
   * @return the response (any status below 500 except 429)
   * @throws TransientNodeException on 5xx, 429, timeouts and connection failures
   * @throws NodeFailureException for an invalid or disallowed URL
   */
  public Response send(String method, String url, Map<String, String> headers, JsonNode body) {
    String effective = aliases.apply(url);
    URI uri = validate(effective);
    HttpRequest.Builder b =
        HttpRequest.newBuilder(uri).timeout(readTimeout).header("Accept", "application/json");
    headers.forEach(b::header);
    if (body != null && !body.isNull() && !"GET".equals(method)) {
      b.header("Content-Type", "application/json");
      b.method(method, HttpRequest.BodyPublishers.ofString(write(body), StandardCharsets.UTF_8));
    } else if ("POST".equals(method) || "PUT".equals(method)) {
      b.header("Content-Type", "application/json");
      b.method(method, HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8));
    } else {
      b.method(method, HttpRequest.BodyPublishers.noBody());
    }
    HttpRequest request = b.build();
    CircuitBreaker breaker = breakers.circuitBreaker(breakerName(uri));
    return breaker.executeSupplier(() -> exchange(request, method, uri, url));
  }

  /** True if the URL targets the configured mock world (after aliasing). */
  public boolean isMockWorld(String url) {
    return stripSlash(aliases.apply(url)).startsWith(mockWorldPrefix);
  }

  private Response exchange(HttpRequest request, String method, URI uri, String originalUrl) {
    HttpResponse<String> res;
    try {
      res = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (HttpTimeoutException e) {
      throw new TransientNodeException(
          ErrorCode.NODE_TIMEOUT, method + " " + hostOf(uri) + " timed out", e);
    } catch (ConnectException e) {
      throw new TransientNodeException(
          ErrorCode.UPSTREAM_UNAVAILABLE, method + " " + hostOf(uri) + " refused the connection", e);
    } catch (IOException e) {
      throw new TransientNodeException(
          ErrorCode.UPSTREAM_UNAVAILABLE,
          method + " " + hostOf(uri) + " failed: " + e.getClass().getSimpleName(),
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TransientNodeException(ErrorCode.NODE_TIMEOUT, "interrupted waiting for response", e);
    }
    int status = res.statusCode();
    if (status >= 500 || status == 429) {
      throw new TransientNodeException(
          ErrorCode.UPSTREAM_UNAVAILABLE,
          method + " " + hostOf(uri) + uri.getPath() + " returned " + status + errorSuffix(res.body()));
    }
    boolean replayed =
        res.headers().firstValue("x-mockworld-replayed").map("true"::equalsIgnoreCase).orElse(false);
    String effective = uri.toString();
    return new Response(
        status,
        res.body(),
        parse(res.body()),
        replayed,
        effective.equals(originalUrl) ? null : effective);
  }

  private URI validate(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new NodeFailureException(ErrorCode.INVALID_PARAM_VALUE, "url is not a valid URI");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
      throw new NodeFailureException(
          ErrorCode.INVALID_PARAM_VALUE, "url must be an absolute http(s) URL");
    }
    if (!allowedHosts.isEmpty() && !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
      throw new NodeFailureException(
          ErrorCode.UPSTREAM_REJECTED,
          "host '" + uri.getHost() + "' is not in RELAY_HTTP_ALLOWED_HOSTS");
    }
    return uri;
  }

  private String breakerName(URI uri) {
    return uri.toString().startsWith(mockWorldPrefix) ? "mockWorld" : "http-" + uri.getHost();
  }

  private JsonNode parse(String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      return mapper.readTree(body);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private String write(JsonNode body) {
    try {
      return mapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new NodeFailureException(ErrorCode.INVALID_PARAM_VALUE, "body is not serializable");
    }
  }

  private String errorSuffix(String body) {
    JsonNode parsed = parse(body);
    String msg = parsed == null ? null : parsed.path("error").path("message").asText(null);
    return msg == null ? "" : " (" + truncate(msg, 160) + ")";
  }

  private static String hostOf(URI uri) {
    return uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
  }

  private static String stripSlash(String s) {
    return s == null ? "" : s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }

  /**
   * A non-transient response.
   *
   * @param status HTTP status
   * @param rawBody raw body text
   * @param json parsed JSON body, or null if not JSON
   * @param replayed mock world replay header ({@code x-mockworld-replayed: true})
   * @param effectiveUrl the URL actually called when an alias rewrote it, else null
   */
  public record Response(
      int status, String rawBody, JsonNode json, boolean replayed, String effectiveUrl) {

    /** True for 2xx. */
    public boolean ok() {
      return status >= 200 && status < 300;
    }

    /** The mock world's {@code error.message}, if present. */
    public String errorMessage() {
      return json == null ? null : json.path("error").path("message").asText(null);
    }
  }
}
