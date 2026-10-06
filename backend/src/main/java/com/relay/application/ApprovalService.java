package com.relay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.application.view.ApprovalView;
import com.relay.domain.error.ConflictException;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.error.NotFoundException;
import com.relay.domain.event.TraceEvent;
import com.relay.domain.run.ApprovalStatus;
import com.relay.domain.run.RunEvent;
import com.relay.domain.run.RunStatus;
import com.relay.domain.run.StepStatus;
import com.relay.domain.workflow.NodeDefinition;
import com.relay.domain.workflow.WorkflowDefinition;
import com.relay.engine.ApprovalDecisionSigner;
import com.relay.engine.queue.WorkQueue;
import com.relay.persistence.entity.ApprovalEntity;
import com.relay.persistence.entity.RunEntity;
import com.relay.persistence.entity.StepEntity;
import com.relay.persistence.repository.ApprovalRepository;
import com.relay.persistence.repository.RunRepository;
import com.relay.persistence.repository.StepRepository;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human decisions on approval nodes.
 *
 * <ul>
 *   <li>Approve → the approval step succeeds with {@code {decision, decided_by, decided_at}}, the
 *       run leaves {@code waiting_approval} for {@code running} and is re-enqueued.
 *   <li>Reject → the approval step is closed and the run ends {@code cancelled} ("reject API ends
 *       it cleanly" — Case Study, Must Have §7; "run ends {@code cancelled}" — API_CONTRACT §4).
 *   <li>Deciding twice → 409 ("Approving an already-decided approval" — API_CONTRACT §7).
 * </ul>
 *
 * Every decision is signed with ML-DSA-65; the engine's {@code ApprovalGate} only honours approvals
 * whose signature verifies. This service is the <em>only</em> code path that can move an approval
 * out of {@code pending} — nothing a model outputs reaches it.
 */
@Service
public class ApprovalService {

  private final ApprovalRepository approvals;
  private final RunRepository runs;
  private final StepRepository steps;
  private final WorkQueue queue;
  private final ApprovalDecisionSigner signer;
  private final RunQueryService queries;
  private final AuditTrail audit;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Creates the service.
   *
   * @param approvals approval repository
   * @param runs run repository
   * @param steps step repository
   * @param queue work queue
   * @param signer decision signer
   * @param queries read side
   * @param audit audit trail
   * @param mapper Jackson mapper
   * @param clock clock
   * @param events event publisher
   */
  public ApprovalService(
      ApprovalRepository approvals,
      RunRepository runs,
      StepRepository steps,
      WorkQueue queue,
      ApprovalDecisionSigner signer,
      RunQueryService queries,
      AuditTrail audit,
      ObjectMapper mapper,
      Clock clock,
      ApplicationEventPublisher events) {
    this.approvals = approvals;
    this.runs = runs;
    this.steps = steps;
    this.queue = queue;
    this.signer = signer;
    this.queries = queries;
    this.audit = audit;
    this.mapper = mapper;
    this.clock = clock;
    this.events = events;
  }

  /**
   * Approves a pending approval and resumes its run.
   *
   * @param approvalId approval id
   * @param actor decider
   * @param comment optional comment
   * @return the decided approval
   */
  @Transactional
  public ApprovalView approve(String approvalId, String actor, String comment) {
    return decide(approvalId, actor, comment, ApprovalStatus.APPROVED);
  }

  /**
   * Rejects a pending approval and cancels its run.
   *
   * @param approvalId approval id
   * @param actor decider
   * @param comment optional comment
   * @return the decided approval
   */
  @Transactional
  public ApprovalView reject(String approvalId, String actor, String comment) {
    return decide(approvalId, actor, comment, ApprovalStatus.REJECTED);
  }

  private ApprovalView decide(
      String approvalId, String actor, String comment, ApprovalStatus decision) {
    ApprovalEntity a =
        approvals.findForUpdate(approvalId).orElseThrow(() -> NotFoundException.approval(approvalId));
    if (a.getStatus() != ApprovalStatus.PENDING) {
      throw new ConflictException(
          ErrorCode.APPROVAL_ALREADY_DECIDED,
          "Approval '" + approvalId + "' is already " + a.getStatus().wire()
              + (a.getDecidedBy() == null ? "" : " (by " + a.getDecidedBy() + ")"),
          Map.of("status", a.getStatus().wire()));
    }
    RunEntity run = runs.findForUpdate(a.getRunId()).orElseThrow();
    if (run.getStatus() != RunStatus.WAITING_APPROVAL) {
      throw new ConflictException(
          ErrorCode.APPROVAL_ALREADY_DECIDED,
          "Run '" + run.getRunId() + "' is " + run.getStatus().wire() + ", not waiting_approval");
    }
    Instant now = clock.instant();
    a.decide(decision, actor, now, comment);
    signer.sign(a);

    StepEntity step =
        steps
            .findFirstByRunIdAndStatusIn(run.getRunId(), List.of(StepStatus.WAITING.wire()))
            .filter(s -> s.getSequence() == a.getStepSequence())
            .orElseThrow(() -> new IllegalStateException("approval step not waiting"));
    ObjectNode out = JsonNodeFactory.instance.objectNode();
    out.put("decision", decision.wire());
    out.put("decided_by", actor);
    out.put("decided_at", now.toString());
    out.put("approval_id", a.getId());
    step.setOutputJson(write(out));

    RunStatus from = run.getStatus();
    if (decision == ApprovalStatus.APPROVED) {
      step.finish(StepStatus.SUCCEEDED, now);
      WorkflowDefinition def = WorkflowDefinition.from(read(run.getDefinitionSnapshotJson()));
      String next = def.node(a.getNodeId()).map(NodeDefinition::next).orElse(null);
      run.setCurrentNodeId(next);
      run.apply(RunEvent.APPROVE, now);
      if (next == null) {
        run.apply(RunEvent.SUCCEED, now);
      } else {
        queue.enqueue(run.getRunId(), now, run.getTraceId());
      }
    } else {
      step.finish(StepStatus.CANCELLED, now);
      ObjectNode err = JsonNodeFactory.instance.objectNode();
      err.put("code", ErrorCode.APPROVAL_REJECTED.code());
      err.put("message", "Approval " + a.getId() + " rejected by " + actor);
      err.putObject("details").put("approval_id", a.getId()).put("node_id", a.getNodeId());
      run.setErrorJson(write(err));
      run.apply(RunEvent.REJECT, now);
    }
    events.publishEvent(
        new TraceEvent.RunStatusChanged(
            run.getRunId(), run.getWorkflowId(), from.wire(), run.getStatus().wire(), now));
    audit.record(
        actor,
        "approval." + (decision == ApprovalStatus.APPROVED ? "approve" : "reject"),
        "approval",
        a.getId(),
        Map.of("run_id", run.getRunId()));
    approvals.flush();
    return queries.approval(a.getId());
  }

  private JsonNode read(String json) {
    try {
      return mapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String write(JsonNode node) {
    try {
      return mapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
