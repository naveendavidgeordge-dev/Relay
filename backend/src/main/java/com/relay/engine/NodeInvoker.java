package com.relay.engine;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.config.RelayProperties;
import com.relay.domain.error.ErrorCode;
import com.relay.nodes.NodeExecutionContext;
import com.relay.nodes.NodeExecutor;
import com.relay.nodes.NodeFailureException;
import com.relay.nodes.NodeResult;
import com.relay.nodes.TransientNodeException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes a node with the resilience policy around it:
 *
 * <ul>
 *   <li><b>Retry</b> (Resilience4j) — exponential backoff with jitter, only for {@link
 *       TransientNodeException}, open circuits and a full bulkhead. Every attempt reuses the
 *       context's idempotency key.
 *   <li><b>Bulkhead</b> per node type ({@code node-<type>}) — a slow AI provider cannot starve
 *       notify or order_action of worker capacity.
 * </ul>
 *
 * Circuit breakers live in the adapters (mock world, AI sidecar) where the dependency is known.
 */
@Component
public class NodeInvoker {

  private static final Logger log = LoggerFactory.getLogger(NodeInvoker.class);

  private final RetryConfig retryConfig;
  private final BulkheadRegistry bulkheads;
  private final Clock clock;

  /**
   * Creates the invoker.
   *
   * @param props relay properties (retry policy)
   * @param bulkheads Resilience4j bulkhead registry (configured in application.yml)
   * @param clock clock
   */
  public NodeInvoker(RelayProperties props, BulkheadRegistry bulkheads, Clock clock) {
    RelayProperties.Retry r = props.retry();
    this.retryConfig =
        RetryConfig.custom()
            .maxAttempts(Math.max(1, r.maxAttempts()))
            .intervalFunction(
                IntervalFunction.ofExponentialRandomBackoff(
                    Duration.ofMillis(r.initialIntervalMs()),
                    r.multiplier(),
                    r.randomizationFactor(),
                    Duration.ofMillis(r.maxIntervalMs())))
            .retryOnException(NodeInvoker::isRetryable)
            .failAfterMaxAttempts(false)
            .build();
    this.bulkheads = bulkheads;
    this.clock = clock;
  }

  private static boolean isRetryable(Throwable t) {
    return t instanceof TransientNodeException
        || t instanceof BulkheadFullException
        || t instanceof CallNotPermittedException;
  }

  /**
   * Runs the node, retrying transient failures.
   *
   * @param executor the strategy
   * @param ctx context (its {@code attempt} is the first attempt number to use)
   * @param onAttempt called with each attempt number before it runs (the engine persists it)
   * @return the outcome, never throws for node failures
   */
  public Invocation invoke(NodeExecutor executor, NodeExecutionContext ctx, IntConsumer onAttempt) {
    Retry retry = Retry.of("node-" + ctx.runId() + "-" + ctx.sequence(), retryConfig);
    Bulkhead bulkhead = bulkheads.bulkhead("node-" + ctx.nodeType());
    AtomicInteger attempt = new AtomicInteger(ctx.attempt() - 1);
    List<ObjectNode> attempts = Collections.synchronizedList(new ArrayList<>());

    Supplier<NodeResult> call =
        () -> {
          int n = attempt.incrementAndGet();
          onAttempt.accept(n);
          Instant started = clock.instant();
          try {
            NodeResult result = executor.execute(ctx.withAttempt(n));
            attempts.add(attemptLog(n, started, "ok", null, null));
            return result;
          } catch (TransientNodeException e) {
            attempts.add(attemptLog(n, started, "transient_error", e.code(), e.getMessage()));
            log.warn("Transient failure on attempt {}: {}", n, e.getMessage());
            throw e;
          } catch (NodeFailureException e) {
            attempts.add(attemptLog(n, started, "failed", e.code(), e.getMessage()));
            throw e;
          }
        };
    Supplier<NodeResult> decorated =
        Retry.decorateSupplier(retry, Bulkhead.decorateSupplier(bulkhead, call));
    try {
      return new Invocation(decorated.get(), null, attempt.get(), List.copyOf(attempts));
    } catch (TransientNodeException e) {
      return failed(
          e.code(),
          "gave up after " + attempt.get() + " attempt(s): " + e.getMessage(),
          Map.of("retryable", true),
          attempt.get(),
          attempts);
    } catch (NodeFailureException e) {
      return failed(e.code(), e.getMessage(), e.details(), attempt.get(), attempts);
    } catch (BulkheadFullException | CallNotPermittedException e) {
      return failed(
          ErrorCode.UPSTREAM_UNAVAILABLE,
          "dependency unavailable after " + attempt.get() + " attempt(s): " + e.getMessage(),
          Map.of("retryable", true),
          attempt.get(),
          attempts);
    } catch (RuntimeException e) {
      log.error("Unexpected executor error", e);
      return failed(
          ErrorCode.INTERNAL_ERROR,
          "unexpected error in the " + ctx.nodeType() + " executor (" + e.getClass().getSimpleName() + ")",
          Map.of(),
          attempt.get(),
          attempts);
    }
  }

  private static Invocation failed(
      ErrorCode code,
      String message,
      Map<String, Object> details,
      int attempts,
      List<ObjectNode> log) {
    return new Invocation(null, new Failure(code, message, details), attempts, List.copyOf(log));
  }

  private ObjectNode attemptLog(
      int n, Instant started, String outcome, ErrorCode code, String message) {
    ObjectNode o = JsonNodeFactory.instance.objectNode();
    o.put("attempt", n);
    o.put("started_at", started.toString());
    o.put("duration_ms", Duration.between(started, clock.instant()).toMillis());
    o.put("outcome", outcome);
    if (code != null) {
      o.put("error_code", code.code());
      o.put("error", message);
    }
    return o;
  }

  /**
   * Outcome of an invocation: exactly one of {@code result} / {@code failure} is set.
   *
   * @param result node result on success
   * @param failure failure description
   * @param attempts attempts used (the last attempt number)
   * @param attemptLog per-attempt log for the trace
   */
  public record Invocation(
      NodeResult result, Failure failure, int attempts, List<ObjectNode> attemptLog) {}

  /**
   * A permanent step failure.
   *
   * @param code error code
   * @param message caller-safe message
   * @param details structured details
   */
  public record Failure(ErrorCode code, String message, Map<String, Object> details) {}
}
