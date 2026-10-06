package com.relay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.application.view.ApprovalView;
import com.relay.application.view.RunView;
import com.relay.domain.catalog.NodeCatalog;
import com.relay.domain.catalog.NodeTypeSpec;
import com.relay.domain.error.NotFoundException;
import com.relay.domain.run.RunStatus;
import com.relay.engine.ApprovalDecisionSigner;
import com.relay.persistence.entity.ApprovalEntity;
import com.relay.persistence.entity.RunEntity;
import com.relay.persistence.entity.StepEntity;
import com.relay.persistence.repository.ApprovalRepository;
import com.relay.persistence.repository.RunRepository;
import com.relay.persistence.repository.StepRepository;
import com.relay.security.crypto.PayloadSigner;
import com.relay.support.CanonicalJson;
import jakarta.persistence.criteria.Predicate;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side for runs, traces and approvals. */
@Service
@Transactional(readOnly = true)
public class RunQueryService {

  private final RunRepository runs;
  private final StepRepository steps;
  private final ApprovalRepository approvals;
  private final NodeCatalog catalog;
  private final PayloadSigner signer;
  private final ApprovalDecisionSigner decisions;
  private final ObjectMapper mapper;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param runs run repository
   * @param steps step repository
   * @param approvals approval repository
   * @param catalog node catalog
   * @param signer snapshot signature verifier
   * @param decisions approval signature verifier
   * @param mapper Jackson mapper
   * @param clock clock
   */
  public RunQueryService(
      RunRepository runs,
      StepRepository steps,
      ApprovalRepository approvals,
      NodeCatalog catalog,
      PayloadSigner signer,
      ApprovalDecisionSigner decisions,
      ObjectMapper mapper,
      Clock clock) {
    this.runs = runs;
    this.steps = steps;
    this.approvals = approvals;
    this.catalog = catalog;
    this.signer = signer;
    this.decisions = decisions;
    this.mapper = mapper;
    this.clock = clock;
  }

  /**
   * The full trace of a run.
   *
   * @param runId run id
   * @return run with steps, approvals and snapshot
   */
  public RunView get(String runId) {
    RunEntity r = runs.findById(runId).orElseThrow(() -> NotFoundException.run(runId));
    JsonNode snapshot = read(r.getDefinitionSnapshotJson());
    boolean valid =
        signer.verify(CanonicalJson.bytes(snapshot), r.getSnapshotKeyId(), r.getSnapshotSignature());
    List<RunView.StepView> trace =
        steps.findAllByRunIdOrderBySequenceAsc(runId).stream().map(this::step).toList();
    List<ApprovalView> apr =
        approvals.findAllByRunIdOrderByCreatedAtAsc(runId).stream()
            .map(a -> approval(a, r.getWorkflowId()))
            .toList();
    return view(r, snapshot, valid, true, apr, trace);
  }

  /**
   * Lists runs, newest first.
   *
   * @param workflowId optional workflow filter
   * @param status optional status filter
   * @param from optional created-at lower bound
   * @param to optional created-at upper bound
   * @param limit max rows (1-500)
   * @return run summaries
   */
  public List<RunView> list(
      Optional<String> workflowId,
      Optional<RunStatus> status,
      Optional<Instant> from,
      Optional<Instant> to,
      int limit) {
    Specification<RunEntity> spec =
        (root, q, cb) -> {
          List<Predicate> ps = new ArrayList<>();
          workflowId.ifPresent(w -> ps.add(cb.equal(root.get("workflowId"), w)));
          status.ifPresent(s -> ps.add(cb.equal(root.get("status"), s.wire())));
          from.ifPresent(f -> ps.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), f)));
          to.ifPresent(t -> ps.add(cb.lessThan(root.<Instant>get("createdAt"), t)));
          return cb.and(ps.toArray(new Predicate[0]));
        };
    int size = Math.max(1, Math.min(limit, 500));
    return runs
        .findAll(spec, PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")))
        .stream()
        .map(r -> view(r, read(r.getDefinitionSnapshotJson()), null, false, null, null))
        .toList();
  }

  /**
   * Approvals, optionally filtered by status.
   *
   * @param status optional status (wire form)
   * @return approvals (pending: oldest first; otherwise newest first)
   */
  public List<ApprovalView> approvals(Optional<String> status) {
    List<ApprovalEntity> rows =
        status.map(approvals::findAllByStatusOrderByCreatedAtAsc)
            .orElseGet(approvals::findAllByOrderByCreatedAtDesc);
    return rows.stream()
        .map(
            a ->
                approval(
                    a, runs.findById(a.getRunId()).map(RunEntity::getWorkflowId).orElse(null)))
        .toList();
  }

  /**
   * One approval.
   *
   * @param id approval id
   * @return the approval
   */
  public ApprovalView approval(String id) {
    ApprovalEntity a = approvals.findById(id).orElseThrow(() -> NotFoundException.approval(id));
    return approval(a, runs.findById(a.getRunId()).map(RunEntity::getWorkflowId).orElse(null));
  }

  private ApprovalView approval(ApprovalEntity a, String workflowId) {
    return new ApprovalView(
        a.getId(),
        a.getRunId(),
        workflowId,
        a.getNodeId(),
        a.getStepSequence(),
        a.getMessage(),
        a.getStatus().wire(),
        a.getCreatedAt(),
        a.getDecidedBy(),
        a.getDecidedAt(),
        a.getDecisionComment(),
        a.getDecisionSignature() == null ? null : decisions.verify(a));
  }

  private RunView view(
      RunEntity r,
      JsonNode snapshot,
      Boolean signatureValid,
      boolean detail,
      List<ApprovalView> apr,
      List<RunView.StepView> trace) {
    JsonNode error = r.getErrorJson() == null ? null : read(r.getErrorJson());
    Instant end = r.getFinishedAt() != null ? r.getFinishedAt() : clock.instant();
    return new RunView(
        r.getRunId(),
        r.getWorkflowId(),
        snapshot.path("name").asText(null),
        r.getWorkflowVersion(),
        r.getStatus().wire(),
        r.getTriggerType().wire(),
        detail ? read(r.getInputJson()) : null,
        r.getCurrentNodeId(),
        r.getStepsExecuted(),
        snapshot.path("limits").has("max_steps") ? snapshot.path("limits").get("max_steps").asInt() : null,
        r.getAiTokensUsed(),
        error,
        error == null ? null : error.path("message").asText(null),
        r.isCancelRequested(),
        r.getCreatedAt(),
        r.getStartedAt(),
        r.getFinishedAt(),
        Duration.between(r.getCreatedAt(), end).toMillis(),
        r.getTraceId(),
        r.getLeaseOwner(),
        signatureValid,
        r.getSnapshotKeyId(),
        detail ? snapshot : null,
        apr,
        trace);
  }

  private RunView.StepView step(StepEntity s) {
    Optional<NodeTypeSpec> spec = catalog.node(s.getNodeType());
    return new RunView.StepView(
        s.getSequence(),
        s.getNodeId(),
        s.getNodeType(),
        s.getStatus().wire(),
        s.getAttempt(),
        s.getResolvedInputJson() == null ? null : read(s.getResolvedInputJson()),
        s.getOutputJson() == null ? null : read(s.getOutputJson()),
        s.getErrorJson() == null ? null : read(s.getErrorJson()),
        s.getStartedAt(),
        s.getFinishedAt(),
        s.getDurationMs(),
        s.getTokensPrompt(),
        s.getTokensCompletion(),
        s.getIdempotencyKey(),
        s.getApprovalId(),
        s.getResumeAt(),
        spec.map(NodeTypeSpec::sideEffect).orElse(false),
        spec.map(NodeTypeSpec::requiresApproval).orElse(false),
        read(s.getAttemptsJson()));
  }

  private JsonNode read(String json) {
    try {
      return mapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
