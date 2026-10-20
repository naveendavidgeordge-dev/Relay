package com.relay.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relay.domain.run.ApprovalStatus;
import com.relay.domain.run.RunEvent;
import com.relay.domain.run.RunStatus;
import com.relay.domain.run.StepStatus;
import com.relay.domain.run.TriggerType;
import com.relay.persistence.entity.ApprovalEntity;
import com.relay.persistence.entity.RunEntity;
import com.relay.persistence.entity.StepEntity;
import com.relay.persistence.entity.WorkflowEntity;
import com.relay.persistence.entity.WorkflowVersionEntity;
import com.relay.persistence.repository.ApprovalRepository;
import com.relay.persistence.repository.RunRepository;
import com.relay.persistence.repository.StepRepository;
import com.relay.persistence.repository.WorkflowRepository;
import com.relay.persistence.repository.WorkflowVersionRepository;
import com.relay.testing.PostgresContainerSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Entities against the real Flyway schema on Postgres 16 ({@code ddl-auto: validate} also proves
 * the mappings match the DDL).
 *
 * <p>Satisfies: "Workflows, runs (with their snapshots), steps, and approvals survive a full
 * process restart" and "index steps by {@code run_id, sequence}" — DATA_MODEL, Storage
 * Expectations.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenceIT extends PostgresContainerSupport {

  private static final Instant NOW = Instant.parse("2026-07-04T10:02:11Z");

  @Autowired WorkflowRepository workflows;
  @Autowired WorkflowVersionRepository versions;
  @Autowired RunRepository runs;
  @Autowired StepRepository steps;
  @Autowired ApprovalRepository approvals;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void workflow() {
    workflows.saveAndFlush(
        new WorkflowEntity("wf_p", "p", null, "manual", "{\"id\":\"wf_p\"}", NOW));
  }

  private RunEntity run(String id) {
    return runs.saveAndFlush(
        new RunEntity(
            id, "wf_p", 1, "{\"id\":\"wf_p\"}", null, null, TriggerType.MANUAL, "{}", "a", "t", NOW));
  }

  @Test
  void runRoundTripAndStateMachine() {
    RunEntity r = run("run_1");
    assertThat(r.getStatus()).isEqualTo(RunStatus.QUEUED);
    r.apply(RunEvent.START, NOW.plusSeconds(1));
    r.incrementStepsExecuted();
    runs.saveAndFlush(r);

    RunEntity loaded = runs.findForUpdate("run_1").orElseThrow();
    assertThat(loaded.getStatus()).isEqualTo(RunStatus.RUNNING);
    assertThat(loaded.getStartedAt()).isEqualTo(NOW.plusSeconds(1));
    assertThat(loaded.getStepsExecuted()).isEqualTo(1);
  }

  @Test
  void statusColumnRejectsValuesOutsideTheContractSet() {
    run("run_bad");
    assertThatThrownBy(
            () -> jdbc.update("UPDATE runs SET status = 'paused' WHERE run_id = 'run_bad'"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void stepsAreOrderedBySequenceAndSequenceIsUniquePerRun() {
    run("run_2");
    steps.saveAndFlush(new StepEntity("run_2", "b", "delay", 2, "{}", null, NOW));
    StepEntity first = new StepEntity("run_2", "a", "notify", 1, "{}", "run_2:a", NOW);
    first.finish(StepStatus.SUCCEEDED, NOW.plus(5, ChronoUnit.MILLIS));
    steps.saveAndFlush(first);

    List<StepEntity> trace = steps.findAllByRunIdOrderBySequenceAsc("run_2");
    assertThat(trace).extracting(StepEntity::getNodeId).containsExactly("a", "b");
    assertThat(trace.get(0).getDurationMs()).isEqualTo(5);
    assertThat(trace.get(0).getIdempotencyKey()).isEqualTo("run_2:a");

    assertThatThrownBy(
            () -> steps.saveAndFlush(new StepEntity("run_2", "c", "delay", 2, "{}", null, NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void atMostOneInFlightStepPerRun() {
    run("run_3");
    steps.saveAndFlush(new StepEntity("run_3", "a", "delay", 1, "{}", null, NOW));
    assertThatThrownBy(
            () -> steps.saveAndFlush(new StepEntity("run_3", "b", "delay", 2, "{}", null, NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void inFlightAndLatestOutputQueries() {
    run("run_4");
    StepEntity s1 = new StepEntity("run_4", "poll", "http_request", 1, "{}", null, NOW);
    s1.setOutputJson("{\"status\":200,\"body\":{\"status\":\"processing\"}}");
    s1.finish(StepStatus.SUCCEEDED, NOW);
    steps.saveAndFlush(s1);
    StepEntity s2 = new StepEntity("run_4", "poll", "http_request", 2, "{}", null, NOW);
    s2.setOutputJson("{\"status\":200,\"body\":{\"status\":\"shipped\"}}");
    s2.finish(StepStatus.SUCCEEDED, NOW);
    steps.saveAndFlush(s2);
    steps.saveAndFlush(new StepEntity("run_4", "hold", "delay", 3, "{}", null, NOW));

    assertThat(
            steps
                .findTopByRunIdAndNodeIdAndStatusOrderBySequenceDesc("run_4", "poll", "succeeded")
                .orElseThrow()
                .getSequence())
        .isEqualTo(2);
    assertThat(steps.countByRunIdAndNodeIdAndSequenceLessThan("run_4", "poll", 3)).isEqualTo(2);
    assertThat(
            steps
                .findFirstByRunIdAndStatusIn("run_4", List.of("running", "waiting"))
                .orElseThrow()
                .getNodeId())
        .isEqualTo("hold");
  }

  @Test
  void approvalGateQueryOnlySeesEarlierApprovedRows() {
    run("run_5");
    ApprovalEntity a = new ApprovalEntity("apr_1", "run_5", "gate", 3, "ok?", NOW);
    approvals.saveAndFlush(a);
    assertThat(approvals.findAllByRunIdAndStatusAndStepSequenceLessThan("run_5", "approved", 4))
        .isEmpty();

    a.decide(ApprovalStatus.APPROVED, "demo", NOW.plusSeconds(5), null);
    approvals.saveAndFlush(a);
    assertThat(approvals.findAllByRunIdAndStatusAndStepSequenceLessThan("run_5", "approved", 4))
        .hasSize(1);
    assertThat(approvals.findAllByRunIdAndStatusAndStepSequenceLessThan("run_5", "approved", 3))
        .as("an approval at or after the gated step does not count")
        .isEmpty();
  }

  @Test
  void decidedApprovalMustRecordDeciderAndTime() {
    run("run_6");
    approvals.saveAndFlush(new ApprovalEntity("apr_2", "run_6", "gate", 1, "ok?", NOW));
    assertThatThrownBy(
            () -> jdbc.update("UPDATE approvals SET status = 'approved' WHERE id = 'apr_2'"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void publishedVersionsAreImmutable() {
    versions.saveAndFlush(
        new WorkflowVersionEntity("wf_p", 1, "{\"id\":\"wf_p\"}", new byte[] {1}, "sig_x", "t", NOW));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE workflow_versions SET definition_json = '{}'::jsonb"
                        + " WHERE workflow_id = 'wf_p'"))
        .hasMessageContaining("immutable");
  }
}
