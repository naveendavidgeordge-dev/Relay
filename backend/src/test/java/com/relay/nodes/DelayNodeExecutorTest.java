package com.relay.nodes;

import static com.relay.testing.Contexts.ctx;
import static com.relay.testing.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Satisfies: "A {@code resume_at} timestamp the worker polls is the more durable design" —
 * IMPLEMENTATION_GUIDE, FAQ; "A durable engine should survive a worker restart during a delay." —
 * node_catalog.json, delay.
 */
class DelayNodeExecutorTest {

  private static final Instant STEP_START = Instant.parse("2026-07-04T10:00:00Z");

  private DelayNodeExecutor at(Instant now) {
    return new DelayNodeExecutor(Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void returnsResumeAtInsteadOfSleeping() {
    NodeResult r = at(STEP_START.plusSeconds(1)).execute(ctx("delay", "pack_delay", json("{\"seconds\": 20}"), null));
    assertThat(r).isEqualTo(NodeResult.sleepUntil(STEP_START.plusSeconds(20)));
  }

  @Test
  void restartedWorkerComputesTheSameResumeAt() {
    // killed 7 s into the delay, restarted at +12 s: resume_at is anchored to the persisted start
    NodeResult r = at(STEP_START.plusSeconds(12)).execute(ctx("delay", "pack_delay", json("{\"seconds\": 20}"), null));
    assertThat(r).isEqualTo(NodeResult.sleepUntil(STEP_START.plusSeconds(20)));
  }

  @Test
  void elapsedDelayCompletesImmediately() {
    NodeResult r = at(STEP_START.plusSeconds(25)).execute(ctx("delay", "pack_delay", json("{\"seconds\": 20}"), null));
    assertThat(r).isInstanceOf(NodeResult.Completed.class);
    assertThat(((NodeResult.Completed) r).output().isEmpty()).isTrue();
  }

  @Test
  void fractionalSeconds() {
    NodeResult r = at(STEP_START).execute(ctx("delay", "hold", json("{\"seconds\": 1.5}"), null));
    assertThat(r).isEqualTo(NodeResult.sleepUntil(STEP_START.plusMillis(1500)));
  }
}
