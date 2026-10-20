package com.relay.nodes;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.relay.domain.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * {@code delay}: "Pauses the run for a number of seconds. A durable engine should survive a worker
 * restart during a delay." — node_catalog.json. Param (verbatim): {@code seconds} (number,
 * required). Output: {@code {}}.
 *
 * <p>Never sleeps a thread. It returns {@code resume_at = step.started_at + seconds}; the engine
 * persists it on the step and releases the queue job until then. Because {@code started_at} is the
 * persisted step start, a worker that is killed and restarted mid-delay computes the same {@code
 * resume_at} and the run continues on time — the kill-and-resume drill kills during exactly this
 * window ({@code wf_slow_fulfillment.pack_delay}).
 */
@Component
public class DelayNodeExecutor implements NodeExecutor {

  private final Clock clock;

  /**
   * Creates the executor.
   *
   * @param clock clock
   */
  public DelayNodeExecutor(Clock clock) {
    this.clock = clock;
  }

  @Override
  public String type() {
    return "delay";
  }

  @Override
  public NodeResult execute(NodeExecutionContext ctx) {
    if (!ctx.param("seconds").isNumber()) {
      throw new NodeFailureException(ErrorCode.INVALID_PARAM_VALUE, "seconds must be a number");
    }
    double seconds = ctx.param("seconds").asDouble();
    if (seconds < 0) {
      throw new NodeFailureException(ErrorCode.INVALID_PARAM_VALUE, "seconds must be >= 0");
    }
    Instant resumeAt = ctx.stepStartedAt().plusMillis(Math.round(seconds * 1000));
    if (!resumeAt.isAfter(clock.instant())) {
      return NodeResult.completed(JsonNodeFactory.instance.objectNode());
    }
    return NodeResult.sleepUntil(resumeAt);
  }
}
