package com.relay.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.application.view.RunView;
import com.relay.domain.error.ConflictException;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.error.NotFoundException;
import com.relay.domain.event.TraceEvent;
import com.relay.domain.run.ApprovalStatus;
import com.relay.domain.run.RunEvent;
import com.relay.domain.run.RunStatus;
import com.relay.domain.run.StepStatus;
import com.relay.engine.queue.WorkQueue;
import com.relay.persistence.entity.RunEntity;
import com.relay.persistence.repository.ApprovalRepository;
import com.relay.persistence.repository.RunRepository;
import com.relay.persistence.repository.StepRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /runs/{runId}/cancel} — API_CONTRACT §5:
 *
 * <ul>
 *   <li>"A {@code queued} run ends {@code cancelled} without executing anything."
 *   <li>"A {@code running} run stops cooperatively: the engine checks for cancellation between
 *       steps." If no worker holds the run (e.g. it is sleeping in a delay) it is cancelled
 *       immediately; otherwise {@code cancel_requested} is set and the worker stops before the
 *       next node.
 *   <li>"A {@code waiting_approval} run ends {@code cancelled} and its pending approval is
 *       closed."
 *   <li>"Cancelling a run that is already terminal … returns 409."
 * </ul>
 *
 * Everything happens under the run's row lock, which the worker also takes for every transition,
 * so a cancel can never interleave with a step being recorded.
 */
@Service
public class CancelService {

  private final RunRepository runs;
  private final StepRepository steps;
  private final ApprovalRepository approvals;
  private final WorkQueue queue;
  private final RunQueryService queries;
  private final AuditTrail audit;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Creates the service.
   *
   * @param runs run repository
   * @param steps step repository
   * @param approvals approval repository
   * @param queue work queue
   * @param queries read side
   * @param audit audit trail
   * @param clock clock
   * @param events event publisher
   */
  public CancelService(
      RunRepository runs,
      StepRepository steps,
      ApprovalRepository approvals,
      WorkQueue queue,
      RunQueryService queries,
      AuditTrail audit,
      Clock clock,
      ApplicationEventPublisher events) {
    this.runs = runs;
    this.steps = steps;
    this.approvals = approvals;
    this.queue = queue;
    this.queries = queries;
    this.audit = audit;
    this.clock = clock;
    this.events = events;
  }

  /**
   * Cancels a run.
   *
   * @param runId run id
   * @param actor caller
   * @return the run after the request ({@code cancelled}, or {@code running} with {@code
   *     cancel_requested=true} while a worker finishes its in-flight step)
   */
  @Transactional
  public RunView cancel(String runId, String actor) {
    RunEntity run = runs.findForUpdate(runId).orElseThrow(() -> NotFoundException.run(runId));
    RunStatus status = run.getStatus();
    if (status.isTerminal()) {
      throw new ConflictException(
          ErrorCode.RUN_ALREADY_TERMINAL,
          "Run '" + runId + "' is already " + status.wire(),
          Map.of("status", status.wire()));
    }
    run.requestCancel(actor);
    boolean immediate =
        switch (status) {
          case QUEUED, WAITING_APPROVAL -> true;
          case RUNNING -> !queue.isLocked(runId);
          default -> false;
        };
    if (immediate) {
      Instant now = clock.instant();
      queue.deleteIfUnlocked(runId);
      steps
          .findFirstByRunIdAndStatusIn(
              runId, List.of(StepStatus.RUNNING.wire(), StepStatus.WAITING.wire()))
          .ifPresent(s -> s.finish(StepStatus.CANCELLED, now));
      approvals.findAllByRunIdOrderByCreatedAtAsc(runId).stream()
          .filter(a -> a.getStatus() == ApprovalStatus.PENDING)
          .forEach(a -> a.decide(ApprovalStatus.CANCELLED, actor, now, "run cancelled"));
      ObjectNode err = JsonNodeFactory.instance.objectNode();
      err.put("code", ErrorCode.CANCELLED.code());
      err.put("message", "Run cancelled by " + actor);
      err.putObject("details").put("cancelled_by", actor).put("previous_status", status.wire());
      run.setErrorJson(err.toString());
      run.apply(RunEvent.CANCEL, now);
      events.publishEvent(
          new TraceEvent.RunStatusChanged(runId, run.getWorkflowId(), status.wire(), "cancelled", now));
    }
    audit.record(actor, "run.cancel", "run", runId, Map.of("immediate", immediate));
    runs.flush();
    return queries.get(runId);
  }
}
