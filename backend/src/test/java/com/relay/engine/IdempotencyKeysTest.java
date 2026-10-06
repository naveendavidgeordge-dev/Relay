package com.relay.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Satisfies: "{@code {run_id}:{node_id}} is the canonical choice. Do not include the attempt
 * number" — API_CONTRACT, Engine Behavior; "If a node legitimately executes twice because the run
 * looped back to it, include the step sequence" — IMPLEMENTATION_GUIDE, FAQ.
 */
class IdempotencyKeysTest {

  @Test
  void firstExecutionUsesTheCanonicalKey() {
    assertThat(IdempotencyKeys.forStep("run_01", "confirm", 1, 0)).isEqualTo("run_01:confirm");
  }

  @Test
  void keyDoesNotDependOnAttemptOrWhenItIsComputed() {
    // The key has no attempt input at all; recomputing for the same step yields the same value.
    String a = IdempotencyKeys.forStep("run_01", "create_shipment", 3, 0);
    String b = IdempotencyKeys.forStep("run_01", "create_shipment", 3, 0);
    assertThat(a).isEqualTo(b).isEqualTo("run_01:create_shipment");
  }

  @Test
  void loopRevisitsAreDistinctEffects() {
    String first = IdempotencyKeys.forStep("run_01", "ping", 1, 0);
    String second = IdempotencyKeys.forStep("run_01", "ping", 4, 1);
    String third = IdempotencyKeys.forStep("run_01", "ping", 7, 2);
    assertThat(first).isEqualTo("run_01:ping");
    assertThat(second).isEqualTo("run_01:ping:s4");
    assertThat(third).isEqualTo("run_01:ping:s7");
  }

  @Test
  void differentRunsNeverCollide() {
    assertThat(IdempotencyKeys.forStep("run_01", "confirm", 1, 0))
        .isNotEqualTo(IdempotencyKeys.forStep("run_02", "confirm", 1, 0));
  }
}
