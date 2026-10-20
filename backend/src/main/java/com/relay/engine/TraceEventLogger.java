package com.relay.engine;

import com.relay.domain.event.TraceEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer of {@link TraceEvent}s: one structured log line per transition and Micrometer metrics
 * ({@code relay.runs.transitions}, {@code relay.steps.finished}, {@code relay.steps.duration}).
 * Runs after commit, so it never reports a rolled-back step. Never logs params or outputs (which
 * may carry payload data); those live only in the trace tables.
 */
@Component
public class TraceEventLogger {

  private static final Logger log = LoggerFactory.getLogger("relay.trace");

  private final MeterRegistry meters;

  /**
   * Creates the listener.
   *
   * @param meters meter registry
   */
  public TraceEventLogger(MeterRegistry meters) {
    this.meters = meters;
  }

  /**
   * Handles an event.
   *
   * @param event the event
   */
  @TransactionalEventListener(fallbackExecution = true)
  public void on(TraceEvent event) {
    switch (event) {
      case TraceEvent.RunStatusChanged e -> {
        log.info("run {} {} -> {} (workflow {})", e.runId(), e.from(), e.to(), e.workflowId());
        meters.counter("relay.runs.transitions", "to", e.to()).increment();
      }
      case TraceEvent.StepStarted e ->
          log.info(
              "step {}#{} ({}) started{}",
              e.nodeId(),
              e.sequence(),
              e.nodeType(),
              e.idempotencyKey() == null ? "" : " idempotency_key=" + e.idempotencyKey());
      case TraceEvent.StepFinished e -> {
        log.info(
            "step {}#{} ({}) {} after {} attempt(s){}",
            e.nodeId(),
            e.sequence(),
            e.nodeType(),
            e.status(),
            e.attempts(),
            e.errorCode() == null ? "" : " error=" + e.errorCode());
        meters
            .counter("relay.steps.finished", "type", e.nodeType(), "status", e.status())
            .increment();
        if (e.durationMs() != null) {
          Timer.builder("relay.steps.duration")
              .tag("type", e.nodeType())
              .register(meters)
              .record(Duration.ofMillis(e.durationMs()));
        }
      }
    }
  }
}
