package com.relay.persistence.repository;

import com.relay.persistence.entity.StepEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@code steps} (the trace). */
public interface StepRepository extends JpaRepository<StepEntity, Long> {

  /** The full trace in execution order (served by the {@code (run_id, sequence)} index). */
  List<StepEntity> findAllByRunIdOrderBySequenceAsc(String runId);

  /**
   * The run's in-flight step, if any ({@code running} or {@code waiting}). At most one exists
   * (partial unique index {@code uq_steps_one_inflight}).
   *
   * @param runId run id
   * @param statuses {@code running}, {@code waiting}
   * @return the step
   */
  Optional<StepEntity> findFirstByRunIdAndStatusIn(String runId, Collection<String> statuses);

  /**
   * The most recent execution of a node with a given status — the source of {@code
   * {{nodes.<id>.output…}}} (latest iteration wins when a run loops).
   *
   * @param runId run id
   * @param nodeId node id
   * @param status step status (wire form)
   * @return the step
   */
  Optional<StepEntity> findTopByRunIdAndNodeIdAndStatusOrderBySequenceDesc(
      String runId, String nodeId, String status);

  /**
   * How many times a node already executed earlier in this run (loop revisits).
   *
   * @param runId run id
   * @param nodeId node id
   * @param sequence current step sequence
   * @return count of earlier executions
   */
  long countByRunIdAndNodeIdAndSequenceLessThan(String runId, String nodeId, int sequence);

  /** Latest step of a run (highest sequence). */
  Optional<StepEntity> findTopByRunIdOrderBySequenceDesc(String runId);
}
