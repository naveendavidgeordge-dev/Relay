package com.relay.domain.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The run state machine.
 *
 * <p>Satisfies: "Runs use a documented status set: {@code queued}, {@code running}, {@code
 * waiting_approval}, {@code succeeded}, {@code failed}, {@code cancelled}." — Case Study, Must Have
 * §7.
 */
class RunStatusTest {

  @Test
  void statusVocabularyIsExactlyTheContractSet() {
    Set<String> wire = Arrays.stream(RunStatus.values()).map(RunStatus::wire).collect(Collectors.toSet());
    assertThat(wire)
        .containsExactlyInAnyOrder(
            "queued", "running", "waiting_approval", "succeeded", "failed", "cancelled");
  }

  @ParameterizedTest(name = "{0} --{1}--> {2}")
  @CsvSource({
    "QUEUED, START, RUNNING",
    "QUEUED, CANCEL, CANCELLED",
    "QUEUED, FAIL, FAILED",
    "RUNNING, PAUSE_FOR_APPROVAL, WAITING_APPROVAL",
    "RUNNING, SUCCEED, SUCCEEDED",
    "RUNNING, FAIL, FAILED",
    "RUNNING, CANCEL, CANCELLED",
    "WAITING_APPROVAL, APPROVE, RUNNING",
    "WAITING_APPROVAL, REJECT, CANCELLED",
    "WAITING_APPROVAL, CANCEL, CANCELLED",
    "WAITING_APPROVAL, FAIL, FAILED"
  })
  void allowedTransitions(RunStatus from, RunEvent event, RunStatus to) {
    assertThat(from.on(event)).isEqualTo(to);
  }

  @ParameterizedTest(name = "{0} --{1}--> rejected")
  @CsvSource({
    "QUEUED, SUCCEED",
    "QUEUED, APPROVE",
    "QUEUED, PAUSE_FOR_APPROVAL",
    "RUNNING, START",
    "RUNNING, APPROVE",
    "RUNNING, REJECT",
    "WAITING_APPROVAL, START",
    "WAITING_APPROVAL, SUCCEED"
  })
  void illegalTransitionsAreRejected(RunStatus from, RunEvent event) {
    assertThatThrownBy(() -> from.on(event))
        .isInstanceOf(IllegalRunTransitionException.class)
        .hasMessageContaining(from.wire());
  }

  @ParameterizedTest
  @EnumSource(
      value = RunStatus.class,
      names = {"SUCCEEDED", "FAILED", "CANCELLED"})
  void terminalStatesAcceptNothing(RunStatus terminal) {
    assertThat(terminal.isTerminal()).isTrue();
    for (RunEvent e : RunEvent.values()) {
      assertThat(terminal.accepts(e)).as("%s on %s", terminal, e).isFalse();
    }
  }

  @Test
  void wireRoundTrip() {
    for (RunStatus s : RunStatus.values()) {
      assertThat(RunStatus.fromWire(s.wire())).isEqualTo(s);
    }
  }
}
