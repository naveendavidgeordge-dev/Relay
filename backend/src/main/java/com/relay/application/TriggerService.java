package com.relay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.relay.domain.error.AuthException;
import com.relay.domain.error.BadRequestException;
import com.relay.domain.error.ConflictException;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.event.TraceEvent;
import com.relay.domain.run.TriggerType;
import com.relay.domain.workflow.WorkflowDefinition;
import com.relay.engine.queue.WorkQueue;
import com.relay.persistence.entity.RunEntity;
import com.relay.persistence.repository.RunRepository;
import com.relay.persistence.repository.WorkflowRepository;
import com.relay.security.crypto.PayloadSigner;
import com.relay.security.secrets.WebhookSecretStore;
import com.relay.support.CanonicalJson;
import com.relay.support.Ids;
import com.relay.support.TraceContext;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts runs. Every trigger does the same three things in one transaction and returns:
 *
 * <ol>
 *   <li>snapshot the latest published definition onto the run and sign it (ML-DSA-65);
 *   <li>insert the run as {@code queued};
 *   <li>enqueue its job.
 * </ol>
 *
 * Nothing executes here: "Both routes must enqueue and return promptly; execution happens on the
 * worker." — API_CONTRACT §2. "Every run captures a snapshot of the definition at trigger time and
 * executes that snapshot" — Case Study, Must Have §2.
 */
@Service
public class TriggerService {

  private final WorkflowService workflows;
  private final WorkflowRepository workflowRepo;
  private final RunRepository runs;
  private final WorkQueue queue;
  private final WebhookSecretStore secrets;
  private final PayloadSigner signer;
  private final AuditTrail audit;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Creates the service.
   *
   * @param workflows workflow service
   * @param workflowRepo workflow repository
   * @param runs run repository
   * @param queue work queue
   * @param secrets webhook secrets
   * @param signer snapshot signer
   * @param audit audit trail
   * @param mapper Jackson mapper
   * @param clock clock
   * @param events event publisher
   */
  public TriggerService(
      WorkflowService workflows,
      WorkflowRepository workflowRepo,
      RunRepository runs,
      WorkQueue queue,
      WebhookSecretStore secrets,
      PayloadSigner signer,
      AuditTrail audit,
      ObjectMapper mapper,
      Clock clock,
      ApplicationEventPublisher events) {
    this.workflows = workflows;
    this.workflowRepo = workflowRepo;
    this.runs = runs;
    this.queue = queue;
    this.secrets = secrets;
    this.signer = signer;
    this.audit = audit;
    this.mapper = mapper;
    this.clock = clock;
    this.events = events;
  }

  /**
   * {@code POST /workflows/{id}/trigger} with {@code {"input": {...}}}.
   *
   * @param workflowId workflow id
   * @param body request body
   * @param actor caller
   * @return the queued run
   */
  @Transactional
  public TriggerResult manual(String workflowId, JsonNode body, String actor) {
    JsonNode input = body == null || body.isNull() ? null : body.get("input");
    if (body != null && !body.isNull() && !body.isObject()) {
      throw new BadRequestException(ErrorCode.INVALID_REQUEST, "Body must be {\"input\": {...}}");
    }
    if (input != null && !input.isNull() && !input.isObject()) {
      throw new BadRequestException(ErrorCode.INVALID_REQUEST, "'input' must be a JSON object");
    }
    WorkflowService.PublishedDefinition p = workflows.latestPublished(workflowId);
    return create(
        p,
        TriggerType.MANUAL,
        input == null || input.isNull() ? JsonNodeFactory.instance.objectNode() : input,
        actor);
  }

  /**
   * {@code POST /hooks/{id}}: authenticated only by {@code X-Relay-Secret}.
   *
   * @param workflowId workflow id
   * @param presentedSecret header value (may be null)
   * @param payload request body; becomes {@code {{trigger.body}}}
   * @return the queued run
   */
  @Transactional
  public TriggerResult webhook(String workflowId, String presentedSecret, JsonNode payload) {
    if (!workflowRepo.existsById(workflowId)) {
      throw com.relay.domain.error.NotFoundException.workflow(workflowId);
    }
    if (presentedSecret == null || presentedSecret.isBlank()) {
      throw new AuthException(
          ErrorCode.MISSING_WEBHOOK_SECRET, "Missing X-Relay-Secret header for this webhook");
    }
    if (!secrets.matches(workflowId, presentedSecret)) {
      throw new AuthException(ErrorCode.INVALID_WEBHOOK_SECRET, "Invalid X-Relay-Secret");
    }
    WorkflowService.PublishedDefinition p = workflows.latestPublished(workflowId);
    if (!"webhook".equals(p.definition().path("trigger").path("type").asText())) {
      throw new ConflictException(
          ErrorCode.TRIGGER_TYPE_MISMATCH,
          "Workflow '" + workflowId + "' is not webhook-triggered in its published version");
    }
    JsonNode body =
        payload == null || payload.isNull() || payload.isMissingNode()
            ? JsonNodeFactory.instance.objectNode()
            : payload;
    return create(p, TriggerType.WEBHOOK, body, "webhook");
  }

  /**
   * Fires a schedule (Good To Have cron trigger).
   *
   * @param workflowId workflow id
   * @return the queued run
   */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public TriggerResult schedule(String workflowId) {
    WorkflowService.PublishedDefinition p = workflows.latestPublished(workflowId);
    return create(p, TriggerType.SCHEDULE, JsonNodeFactory.instance.objectNode(), "system:schedule");
  }

  private TriggerResult create(
      WorkflowService.PublishedDefinition p, TriggerType type, JsonNode input, String actor) {
    Instant now = clock.instant();
    String runId = Ids.runId();
    String traceId = TraceContext.currentOrNew();
    JsonNode snapshot = p.definition();
    PayloadSigner.Signature sig = signer.sign(CanonicalJson.bytes(snapshot));
    WorkflowDefinition def = WorkflowDefinition.from(snapshot);
    RunEntity run =
        new RunEntity(
            runId,
            p.workflowId(),
            p.version(),
            write(snapshot),
            sig.bytes(),
            sig.keyId(),
            type,
            write(input),
            def.entry(),
            traceId,
            now);
    runs.saveAndFlush(run); // flush: the JDBC queue insert references runs(run_id)
    queue.enqueue(runId, now, traceId);
    audit.record(
        actor,
        "run.trigger",
        "run",
        runId,
        Map.of("workflow_id", p.workflowId(), "version", p.version(), "trigger", type.wire()));
    events.publishEvent(
        new TraceEvent.RunStatusChanged(runId, p.workflowId(), null, "queued", now));
    return new TriggerResult(runId, "queued", p.workflowId(), p.version(), type.wire());
  }

  private String write(JsonNode node) {
    try {
      return mapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Trigger response: {@code {"run_id": "run_…", "status": "queued", …}}.
   *
   * @param runId run id (fixed contract field)
   * @param status always {@code queued}
   * @param workflowId workflow id
   * @param workflowVersion snapshotted version
   * @param triggerType trigger type
   */
  public record TriggerResult(
      String runId, String status, String workflowId, int workflowVersion, String triggerType) {}
}
