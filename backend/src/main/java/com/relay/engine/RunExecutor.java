package com.relay.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.config.RelayProperties;
import com.relay.domain.catalog.NodeCatalog;
import com.relay.domain.catalog.NodeTypeSpec;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.event.TraceEvent;
import com.relay.domain.run.ApprovalStatus;
import com.relay.domain.run.RunEvent;
import com.relay.domain.run.RunStatus;
import com.relay.domain.run.StepStatus;
import com.relay.domain.workflow.Limits;
import com.relay.domain.workflow.NodeDefinition;
import com.relay.domain.workflow.WorkflowDefinition;
import com.relay.engine.template.TemplateResolutionException;
import com.relay.engine.template.TemplateResolver;
import com.relay.nodes.NodeExecutionContext;
import com.relay.nodes.NodeExecutor;
import com.relay.nodes.NodeResult;
import com.relay.persistence.entity.ApprovalEntity;
import com.relay.persistence.entity.RunEntity;
import com.relay.persistence.entity.StepEntity;
import com.relay.persistence.repository.ApprovalRepository;
import com.relay.persistence.repository.RunRepository;
import com.relay.persistence.repository.StepRepository;
import com.relay.security.crypto.PayloadSigner;
import com.relay.support.CanonicalJson;
import com.relay.support.Ids;
import com.relay.support.TraceContext;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Advances one run as far as it can go: until it finishes, pauses for a human, or must wait for a
 * delay. The worker calls {@link #advance} while holding the run's queue lease.
 *
 * <p>Every iteration follows the durability protocol (DESIGN/04):
 *
 * <ol>
 *   <li><b>Plan</b> (transaction, run row locked): honour cancellation; resume an in-flight step if
 *       a worker died mid-step; otherwise check the step cap, resolve templates, enforce the
 *       approval gate, derive the idempotency key, and <b>persist the step intent</b>.
 *   <li><b>Act</b> (no transaction): run the node with retries; side effects carry the persisted
 *       key.
 *   <li><b>Record</b> (transaction, run row locked): <b>persist the output before advancing</b>
 *       {@code current_node_id}.
 * </ol>
 *
 * "Persist, then acknowledge. A step's completion (including its output and idempotency key) must
 * be persisted before the engine moves to the next node. Resume logic derives entirely from
 * persisted steps." — DATA_MODEL, Correctness Requirements
 */
@Component
public class RunExecutor {

  private static final Logger log = LoggerFactory.getLogger(RunExecutor.class);
  private static final List<String> IN_FLIGHT =
      List.of(StepStatus.RUNNING.wire(), StepStatus.WAITING.wire());

  private final RunRepository runs;
  private final StepRepository steps;
  private final ApprovalRepository approvals;
  private final NodeCatalog catalog;
  private final NodeExecutorRegistry registry;
  private final TemplateResolver resolver;
  private final ApprovalGate gate;
  private final NodeInvoker invoker;
  private final PayloadSigner signer;
  private final TransactionTemplate tx;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final ApplicationEventPublisher events;
  private final RelayProperties props;

  /**
   * Creates the executor.
   *
   * @param runs run repository
   * @param steps step repository
   * @param approvals approval repository
   * @param catalog node catalog
   * @param registry executor registry
   * @param resolver template resolver
   * @param gate approval gate
   * @param invoker retrying node invoker
   * @param signer snapshot signature verifier
   * @param tx transaction template
   * @param mapper Jackson mapper
   * @param clock clock
   * @param events domain event publisher
   * @param props relay properties
   */
  public RunExecutor(
      RunRepository runs,
      StepRepository steps,
      ApprovalRepository approvals,
      NodeCatalog catalog,
      NodeExecutorRegistry registry,
      TemplateResolver resolver,
      ApprovalGate gate,
      NodeInvoker invoker,
      PayloadSigner signer,
      TransactionTemplate tx,
      ObjectMapper mapper,
      Clock clock,
      ApplicationEventPublisher events,
      RelayProperties props) {
    this.runs = runs;
    this.steps = steps;
    this.approvals = approvals;
    this.catalog = catalog;
    this.registry = registry;
    this.resolver = resolver;
    this.gate = gate;
    this.invoker = invoker;
    this.signer = signer;
    this.tx = tx;
    this.mapper = mapper;
    this.clock = clock;
    this.events = events;
    this.props = props;
  }

  /**
   * Advances a run.
   *
   * @param runId run id
   * @param workerId worker holding the lease
   * @param leaseUntil current lease expiry (mirrored onto the run for visibility)
   * @param leaseLost true once the worker lost its lease (stop touching the run)
   * @return what to do with the queue job
   */
  public JobOutcome advance(
      String runId, String workerId, Instant leaseUntil, BooleanSupplier leaseLost) {
    Start start = tx.execute(s -> start(runId, workerId, leaseUntil));
    if (start == null || start.definition() == null) {
      return JobOutcome.COMPLETE;
    }
    while (true) {
      if (leaseLost.getAsBoolean()) {
        log.warn("Lease lost; abandoning run {} to its new owner", runId);
        return JobOutcome.ABANDON;
      }
      Plan plan = tx.execute(s -> plan(runId, start.definition(), start.input()));
      if (plan instanceof Plan.Finished || plan == null) {
        return JobOutcome.COMPLETE;
      }
      if (plan instanceof Plan.Sleep sleep) {
        return JobOutcome.rescheduleAt(sleep.until());
      }
      if (plan instanceof Plan.Continue) {
        continue;
      }
      Plan.Execute exec = (Plan.Execute) plan;
      NodeInvoker.Invocation invocation = act(runId, start, exec);
      JobOutcome outcome = tx.execute(s -> record(runId, exec, invocation));
      if (outcome != null) {
        return outcome;
      }
    }
  }

  /**
   * Fails a run whose job keeps crashing the worker (poison message), so it cannot loop forever.
   *
   * @param runId run id
   * @param message reason
   */
  public void failPoisoned(String runId, String message) {
    tx.executeWithoutResult(
        s ->
            runs.findForUpdate(runId)
                .filter(r -> !r.getStatus().isTerminal())
                .ifPresent(
                    r -> {
                      steps
                          .findFirstByRunIdAndStatusIn(runId, IN_FLIGHT)
                          .ifPresent(st -> st.finish(StepStatus.FAILED, clock.instant()));
                      failRun(r, ErrorCode.INTERNAL_ERROR, message, Map.of());
                    }));
  }

  // ------------------------------------------------------------------ start

  private Start start(String runId, String workerId, Instant leaseUntil) {
    Optional<RunEntity> found = runs.findForUpdate(runId);
    if (found.isEmpty()) {
      return null;
    }
    RunEntity run = found.get();
    RunStatus status = run.getStatus();
    if (status.isTerminal() || status == RunStatus.WAITING_APPROVAL) {
      return null;
    }
    JsonNode snapshot = read(run.getDefinitionSnapshotJson());
    // Tamper evidence: the snapshot must still match the ML-DSA signature taken at trigger time.
    if (!signer.verify(
        CanonicalJson.bytes(snapshot), run.getSnapshotKeyId(), run.getSnapshotSignature())) {
      failRun(
          run,
          ErrorCode.SNAPSHOT_SIGNATURE_INVALID,
          "Definition snapshot signature does not verify; refusing to execute a modified snapshot",
          Map.of());
      return null;
    }
    if (status == RunStatus.QUEUED) {
      transition(run, RunEvent.START);
    }
    run.setLease(workerId, leaseUntil);
    return new Start(WorkflowDefinition.from(snapshot), read(run.getInputJson()));
  }

  // ------------------------------------------------------------------ plan

  private Plan plan(String runId, WorkflowDefinition def, JsonNode input) {
    RunEntity run = runs.findForUpdate(runId).orElse(null);
    if (run == null
        || run.getStatus().isTerminal()
        || run.getStatus() == RunStatus.WAITING_APPROVAL) {
      return Plan.FINISHED;
    }
    Instant now = clock.instant();
    Optional<StepEntity> inFlight = steps.findFirstByRunIdAndStatusIn(runId, IN_FLIGHT);

    // Cooperative cancellation, checked between steps (API_CONTRACT §5).
    if (run.isCancelRequested()) {
      cancel(run, inFlight.orElse(null), now);
      return Plan.FINISHED;
    }

    if (inFlight.isPresent()) {
      return resumeInFlight(run, def, inFlight.get(), now);
    }

    String nodeId = run.getCurrentNodeId();
    if (nodeId == null) {
      transition(run, RunEvent.SUCCEED);
      return Plan.FINISHED;
    }

    // Caps are checked BEFORE each node executes ("Step cap checked in the engine loop").
    Limits limits = def.limits();
    if (run.getStepsExecuted() >= limits.maxSteps()) {
      failRun(
          run,
          ErrorCode.MAX_STEPS_EXCEEDED,
          limits.maxStepsReason(),
          ordered(
              "cap", "max_steps",
              "limit", limits.maxSteps(),
              "steps_executed", run.getStepsExecuted(),
              "next_node_id", nodeId));
      return Plan.FINISHED;
    }
    if (limits.maxAiTokens() != null
        && limits.maxAiTokens() > 0
        && run.getAiTokensUsed() >= limits.maxAiTokens()) {
      failRun(
          run,
          ErrorCode.TOKEN_BUDGET_EXCEEDED,
          "max_ai_tokens cap (" + limits.maxAiTokens() + ") exceeded",
          ordered("cap", "max_ai_tokens", "limit", limits.maxAiTokens(), "used", run.getAiTokensUsed()));
      return Plan.FINISHED;
    }
    if (props.engine().enforceTimeout()
        && limits.timeoutSeconds() != null
        && run.getStartedAt() != null
        && now.isAfter(run.getStartedAt().plusSeconds(limits.timeoutSeconds()))) {
      failRun(
          run,
          ErrorCode.TIMEOUT_EXCEEDED,
          "timeout_seconds cap (" + limits.timeoutSeconds() + ") exceeded",
          ordered("cap", "timeout_seconds", "limit", limits.timeoutSeconds()));
      return Plan.FINISHED;
    }

    NodeDefinition node = def.node(nodeId).orElse(null);
    NodeTypeSpec spec = node == null ? null : catalog.node(node.type()).orElse(null);
    if (node == null || spec == null) {
      failRun(
          run,
          ErrorCode.INTERNAL_ERROR,
          "Snapshot references node '" + nodeId + "' that is missing or not in the catalog",
          Map.of());
      return Plan.FINISHED;
    }

    int sequence = run.incrementStepsExecuted();

    ObjectNode resolved;
    try {
      resolved = resolver.resolve(spec, node.params(), scope(runId, input));
    } catch (TemplateResolutionException e) {
      StepEntity failed =
          new StepEntity(runId, node.id(), node.type(), sequence, write(node.params()), null, now);
      failStep(failed, ErrorCode.TEMPLATE_UNRESOLVABLE, e.getMessage(),
          ordered("field", e.field(), "expression", e.expression()), now);
      failRun(
          run,
          ErrorCode.TEMPLATE_UNRESOLVABLE,
          "Step '" + node.id() + "' failed: " + e.getMessage(),
          ordered("node_id", node.id(), "sequence", sequence));
      return Plan.FINISHED;
    }

    // Engine-enforced approval gate — a query over approval rows, never a prompt.
    String authorizingApproval = null;
    if (spec.requiresApproval()) {
      Optional<ApprovalEntity> approval = gate.authorizing(runId, sequence);
      if (approval.isEmpty()) {
        String msg =
            "Node '" + node.id() + "' (" + node.type() + ") requires an approved approval earlier"
                + " in this run; none found. Refusing to execute.";
        StepEntity refused =
            new StepEntity(runId, node.id(), node.type(), sequence, write(resolved), null, now);
        failStep(refused, ErrorCode.APPROVAL_REQUIRED, msg, Map.of(), now);
        failRun(run, ErrorCode.APPROVAL_REQUIRED, msg, ordered("node_id", node.id()));
        return Plan.FINISHED;
      }
      authorizingApproval = approval.get().getId();
    }

    String key = null;
    if (needsIdempotencyKey(spec, resolved)) {
      long prior = steps.countByRunIdAndNodeIdAndSequenceLessThan(runId, node.id(), sequence);
      key = IdempotencyKeys.forStep(runId, node.id(), sequence, prior);
    }

    // Persist intent BEFORE acting: resolved input + idempotency key are now durable.
    StepEntity step =
        new StepEntity(runId, node.id(), node.type(), sequence, write(resolved), key, now);
    step.setApprovalId(authorizingApproval);
    steps.saveAndFlush(step);
    events.publishEvent(
        new TraceEvent.StepStarted(runId, node.id(), node.type(), sequence, key, now));
    return execute(run, step, node, resolved);
  }

  private Plan resumeInFlight(RunEntity run, WorkflowDefinition def, StepEntity s, Instant now) {
    NodeDefinition node = def.node(s.getNodeId()).orElse(null);
    if (node == null) {
      failStep(s, ErrorCode.INTERNAL_ERROR, "node vanished from snapshot", Map.of(), now);
      failRun(run, ErrorCode.INTERNAL_ERROR, "In-flight node missing from snapshot", Map.of());
      return Plan.FINISHED;
    }
    if (s.getStatus() == StepStatus.WAITING) {
      if (!"delay".equals(s.getNodeType())) {
        return Plan.FINISHED; // approval wait: only the approvals API resumes it
      }
      if (s.getResumeAt() != null && now.isBefore(s.getResumeAt())) {
        return new Plan.Sleep(s.getResumeAt());
      }
      ObjectNode out = JsonNodeFactory.instance.objectNode();
      out.put("resume_at", String.valueOf(s.getResumeAt()));
      out.put("resumed_at", now.toString());
      s.setOutputJson(write(out));
      s.finish(StepStatus.SUCCEEDED, now);
      publishFinished(s, null);
      advancePast(run, node, out);
      return run.getStatus().isTerminal() ? Plan.FINISHED : Plan.CONTINUE;
    }
    // RUNNING: the previous worker died mid-step (the crash window). Re-execute with the SAME
    // persisted resolved input and idempotency key; the mock world absorbs a replay if the first
    // attempt landed ("re-send with the same key and let replay detection resolve it").
    appendLog(s, List.of(event("resumed_after_worker_loss", s.getAttempt() + 1, now)));
    s.setAttempt(s.getAttempt() + 1);
    log.info(
        "Resuming in-flight step {} (seq {}) of run {} with key {}",
        s.getNodeId(),
        s.getSequence(),
        run.getRunId(),
        s.getIdempotencyKey());
    return execute(run, s, node, read(s.getResolvedInputJson()));
  }

  private Plan execute(RunEntity run, StepEntity step, NodeDefinition node, JsonNode params) {
    NodeExecutionContext ctx =
        NodeExecutionContext.builder()
            .runId(run.getRunId())
            .workflowId(run.getWorkflowId())
            .nodeId(node.id())
            .nodeType(node.type())
            .sequence(step.getSequence())
            .attempt(step.getAttempt())
            .params(params)
            .idempotencyKey(step.getIdempotencyKey())
            .stepStartedAt(step.getStartedAt())
            .traceId(run.getTraceId())
            .build();
    return new Plan.Execute(step.getId(), node, ctx);
  }

  // ------------------------------------------------------------------ act

  private NodeInvoker.Invocation act(String runId, Start start, Plan.Execute exec) {
    Optional<NodeExecutor> executor = registry.get(exec.node().type());
    if (executor.isEmpty()) {
      return new NodeInvoker.Invocation(
          null,
          new NodeInvoker.Failure(
              ErrorCode.NO_EXECUTOR, "No executor registered for '" + exec.node().type() + "'", Map.of()),
          exec.ctx().attempt(),
          List.of());
    }
    MDC.put(TraceContext.NODE_ID, exec.node().id());
    try {
      return invoker.invoke(
          executor.get(),
          exec.ctx(),
          attempt ->
              tx.executeWithoutResult(
                  s -> steps.findById(exec.stepId()).ifPresent(st -> st.setAttempt(attempt))));
    } finally {
      MDC.remove(TraceContext.NODE_ID);
    }
  }

  // ------------------------------------------------------------------ record

  private JobOutcome record(String runId, Plan.Execute exec, NodeInvoker.Invocation inv) {
    RunEntity run = runs.findForUpdate(runId).orElse(null);
    StepEntity step = steps.findById(exec.stepId()).orElse(null);
    if (run == null || step == null) {
      return JobOutcome.COMPLETE;
    }
    Instant now = clock.instant();
    if (step.getStatus() != StepStatus.RUNNING) {
      // Closed concurrently (e.g. cancellation of a sleeping run); the run decides what is next.
      return run.getStatus().isTerminal() ? JobOutcome.COMPLETE : null;
    }
    step.setAttempt(Math.max(step.getAttempt(), inv.attempts()));
    appendLog(step, inv.attemptLog());
    NodeDefinition node = exec.node();

    if (inv.failure() != null) {
      NodeInvoker.Failure f = inv.failure();
      // AI steps that fail schema validation still consumed tokens; keep the trace honest.
      if (f.details().get("tokens_prompt") instanceof Integer tp
          && f.details().get("tokens_completion") instanceof Integer tc) {
        step.setTokens(tp, tc);
        run.addAiTokens(tp + tc);
      }
      failStep(step, f.code(), f.message(), f.details(), now);
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("node_id", node.id());
      details.put("sequence", step.getSequence());
      details.put("attempts", inv.attempts());
      failRun(
          run,
          f.code(),
          "Step '" + node.id() + "' (" + node.type() + ") failed: " + f.message(),
          details);
      return JobOutcome.COMPLETE;
    }

    NodeResult result = inv.result();
    if (result instanceof NodeResult.Completed c) {
      appendLog(step, c.details());
      step.setOutputJson(write(c.output()));
      if (c.tokens() != null) {
        step.setTokens(c.tokens().prompt(), c.tokens().completion());
        run.addAiTokens(c.tokens().total());
      }
      step.finish(StepStatus.SUCCEEDED, now);
      publishFinished(step, null);
      advancePast(run, node, c.output());
      return run.getStatus().isTerminal() ? JobOutcome.COMPLETE : null;
    }
    if (result instanceof NodeResult.AwaitApproval a) {
      ApprovalEntity approval =
          approvals
              .findByRunIdAndStepSequence(runId, step.getSequence())
              .orElseGet(
                  () ->
                      approvals.save(
                          new ApprovalEntity(
                              Ids.approvalId(), runId, node.id(), step.getSequence(), a.message(), now)));
      step.markWaiting();
      step.setApprovalId(approval.getId());
      ObjectNode out = JsonNodeFactory.instance.objectNode();
      out.put("approval_id", approval.getId());
      out.put("status", ApprovalStatus.PENDING.wire());
      step.setOutputJson(write(out));
      publishFinished(step, null);
      transition(run, RunEvent.PAUSE_FOR_APPROVAL);
      return JobOutcome.COMPLETE; // no job while a human decides; approve re-enqueues
    }
    NodeResult.SleepUntil sleep = (NodeResult.SleepUntil) result;
    step.markWaiting();
    step.setResumeAt(sleep.until());
    ObjectNode out = JsonNodeFactory.instance.objectNode();
    out.put("resume_at", sleep.until().toString());
    step.setOutputJson(write(out));
    return JobOutcome.rescheduleAt(sleep.until());
  }

  // ------------------------------------------------------------------ helpers

  private void advancePast(RunEntity run, NodeDefinition node, JsonNode output) {
    boolean branching = catalog.node(node.type()).map(NodeTypeSpec::isBranching).orElse(false);
    String next =
        branching
            ? (output != null && output.path("result").asBoolean(false) ? node.onTrue() : node.onFalse())
            : node.next();
    run.setCurrentNodeId(next);
    if (next == null) {
      transition(run, RunEvent.SUCCEED);
    }
  }

  private void cancel(RunEntity run, StepEntity inFlight, Instant now) {
    if (inFlight != null) {
      inFlight.setErrorJson(write(error(ErrorCode.CANCELLED, "Run cancelled", Map.of())));
      inFlight.finish(StepStatus.CANCELLED, now);
      publishFinished(inFlight, ErrorCode.CANCELLED);
    }
    approvals.findAllByRunIdOrderByCreatedAtAsc(run.getRunId()).stream()
        .filter(a -> a.getStatus() == ApprovalStatus.PENDING)
        .forEach(a -> a.decide(ApprovalStatus.CANCELLED, run.getCancelledBy(), now, "run cancelled"));
    run.setErrorJson(
        write(
            error(
                ErrorCode.CANCELLED,
                "Run cancelled by " + run.getCancelledBy(),
                ordered("cancelled_by", run.getCancelledBy()))));
    transition(run, RunEvent.CANCEL);
  }

  private void failStep(
      StepEntity step, ErrorCode code, String message, Map<String, Object> details, Instant now) {
    step.setErrorJson(write(error(code, message, details)));
    step.finish(StepStatus.FAILED, now);
    if (step.getId() == null) {
      steps.save(step);
    }
    publishFinished(step, code);
  }

  private void failRun(RunEntity run, ErrorCode code, String message, Map<String, Object> details) {
    run.setErrorJson(write(error(code, message, details)));
    transition(run, RunEvent.FAIL);
    log.info("Run {} failed: {} ({})", run.getRunId(), message, code.code());
  }

  private void transition(RunEntity run, RunEvent event) {
    RunStatus from = run.getStatus();
    RunStatus to = run.apply(event, clock.instant());
    events.publishEvent(
        new TraceEvent.RunStatusChanged(
            run.getRunId(), run.getWorkflowId(), from.wire(), to.wire(), clock.instant()));
  }

  private void publishFinished(StepEntity s, ErrorCode code) {
    events.publishEvent(
        new TraceEvent.StepFinished(
            s.getRunId(),
            s.getNodeId(),
            s.getNodeType(),
            s.getSequence(),
            s.getStatus().wire(),
            s.getAttempt(),
            s.getDurationMs(),
            code == null ? null : code.code(),
            clock.instant()));
  }

  private TemplateResolver.Scope scope(String runId, JsonNode input) {
    return new TemplateResolver.Scope(
        input,
        nodeId ->
            steps
                .findTopByRunIdAndNodeIdAndStatusOrderBySequenceDesc(
                    runId, nodeId, StepStatus.SUCCEEDED.wire())
                .map(st -> st.getOutputJson() == null ? null : read(st.getOutputJson())));
  }

  /** Side-effect nodes need a key; a GET {@code http_request} is a read and does not. */
  private static boolean needsIdempotencyKey(NodeTypeSpec spec, JsonNode resolved) {
    if (!spec.sideEffect()) {
      return false;
    }
    return !("http_request".equals(spec.type())
        && "GET".equalsIgnoreCase(resolved.path("method").asText()));
  }

  private void appendLog(StepEntity step, List<ObjectNode> entries) {
    if (entries == null || entries.isEmpty()) {
      return;
    }
    JsonNode current = read(step.getAttemptsJson() == null ? "[]" : step.getAttemptsJson());
    ArrayNode arr = current.isArray() ? (ArrayNode) current : JsonNodeFactory.instance.arrayNode();
    entries.forEach(arr::add);
    step.setAttemptsJson(write(arr));
  }

  private static ObjectNode event(String name, int attempt, Instant at) {
    ObjectNode o = JsonNodeFactory.instance.objectNode();
    o.put("event", name);
    o.put("attempt", attempt);
    o.put("at", at.toString());
    return o;
  }

  private ObjectNode error(ErrorCode code, String message, Map<String, Object> details) {
    ObjectNode o = JsonNodeFactory.instance.objectNode();
    o.put("code", code.code());
    o.put("message", message);
    if (details != null && !details.isEmpty()) {
      o.set("details", mapper.valueToTree(details));
    }
    return o;
  }

  private static Map<String, Object> ordered(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      if (kv[i + 1] != null) {
        m.put(String.valueOf(kv[i]), kv[i + 1]);
      }
    }
    return m;
  }

  private JsonNode read(String json) {
    try {
      return json == null ? JsonNodeFactory.instance.nullNode() : mapper.readTree(json);
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

  /** State loaded once per job. */
  private record Start(WorkflowDefinition definition, JsonNode input) {}

  /** The planner's decision for the next iteration. */
  private sealed interface Plan permits Plan.Finished, Plan.Continue, Plan.Sleep, Plan.Execute {
    Plan FINISHED = new Finished();
    Plan CONTINUE = new Continue();

    record Finished() implements Plan {}

    record Continue() implements Plan {}

    record Sleep(Instant until) implements Plan {}

    record Execute(long stepId, NodeDefinition node, NodeExecutionContext ctx) implements Plan {}
  }
}
