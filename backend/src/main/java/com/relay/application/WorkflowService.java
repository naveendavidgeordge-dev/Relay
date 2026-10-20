package com.relay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.application.view.ValidationView;
import com.relay.application.view.WorkflowView;
import com.relay.domain.error.BadRequestException;
import com.relay.domain.error.ConflictException;
import com.relay.domain.error.DefinitionValidationException;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.error.NotFoundException;
import com.relay.domain.validation.PublishValidator;
import com.relay.domain.validation.ValidationReport;
import com.relay.domain.workflow.DefinitionSecrets;
import com.relay.domain.workflow.WorkflowStatus;
import com.relay.persistence.entity.WorkflowEntity;
import com.relay.persistence.entity.WorkflowVersionEntity;
import com.relay.persistence.query.WorkflowRunStatsQuery;
import com.relay.persistence.repository.WorkflowRepository;
import com.relay.persistence.repository.WorkflowVersionRepository;
import com.relay.security.crypto.PayloadSigner;
import com.relay.security.secrets.WebhookSecretStore;
import com.relay.support.CanonicalJson;
import com.relay.support.Ids;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workflow lifecycle: create (draft) → update working copy → publish (frozen, signed version).
 *
 * <p>"Two states: Draft (editable) and Published (frozen, runnable)." — Case Study, Must Have §2.
 * Each publish freezes the working copy into an immutable {@code workflow_versions} row; editing a
 * published workflow only changes its working copy until the next publish (DESIGN/00 conflict C4).
 */
@Service
public class WorkflowService {

  /** Allowed workflow / node id characters. */
  public static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-]{1,128}$");

  private final WorkflowRepository workflows;
  private final WorkflowVersionRepository versions;
  private final WorkflowRunStatsQuery runStats;
  private final PublishValidator validator;
  private final WebhookSecretStore secrets;
  private final PayloadSigner signer;
  private final AuditTrail audit;
  private final ScheduleSync scheduleSync;
  private final ObjectMapper mapper;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param workflows workflow repository
   * @param versions version repository
   * @param runStats run stats query
   * @param validator publish validator
   * @param secrets webhook secret store
   * @param signer ML-DSA signer
   * @param audit audit trail
   * @param scheduleSync schedule sync (cron triggers)
   * @param mapper Jackson mapper
   * @param clock clock
   */
  public WorkflowService(
      WorkflowRepository workflows,
      WorkflowVersionRepository versions,
      WorkflowRunStatsQuery runStats,
      PublishValidator validator,
      WebhookSecretStore secrets,
      PayloadSigner signer,
      AuditTrail audit,
      ScheduleSync scheduleSync,
      ObjectMapper mapper,
      Clock clock) {
    this.workflows = workflows;
    this.versions = versions;
    this.runStats = runStats;
    this.validator = validator;
    this.secrets = secrets;
    this.signer = signer;
    this.audit = audit;
    this.scheduleSync = scheduleSync;
    this.mapper = mapper;
    this.clock = clock;
  }

  // ---------------------------------------------------------------- queries

  /**
   * Lists workflows (summary fields only).
   *
   * @param status optional status filter
   * @return workflows, oldest first
   */
  @Transactional(readOnly = true)
  public List<WorkflowView> list(Optional<WorkflowStatus> status) {
    Map<String, WorkflowRunStatsQuery.Stats> stats = runStats.all();
    List<WorkflowEntity> rows =
        status
            .map(s -> workflows.findAllByStatusOrderByCreatedAtAscIdAsc(s.wire()))
            .orElseGet(workflows::findAllByOrderByCreatedAtAscIdAsc);
    return rows.stream()
        .map(w -> summary(w, stats.getOrDefault(w.getId(), WorkflowRunStatsQuery.Stats.NONE)))
        .toList();
  }

  /**
   * Full workflow detail including definitions, versions and the working copy's validation.
   *
   * @param id workflow id
   * @return the detail view
   */
  @Transactional(readOnly = true)
  public WorkflowView get(String id) {
    WorkflowEntity w = find(id);
    WorkflowRunStatsQuery.Stats stats =
        runStats.all().getOrDefault(id, WorkflowRunStatsQuery.Stats.NONE);
    return detail(w, stats);
  }

  /**
   * The latest published definition (secret-free) — the thing a trigger snapshots.
   *
   * @param id workflow id
   * @return version number and definition
   * @throws ConflictException if the workflow was never published
   */
  @Transactional(readOnly = true)
  public PublishedDefinition latestPublished(String id) {
    WorkflowEntity w = find(id);
    if (w.getPublishedVersion() == null) {
      throw new ConflictException(
          ErrorCode.WORKFLOW_NOT_PUBLISHED,
          "Workflow '" + id + "' is not published; publish it before triggering");
    }
    WorkflowVersionEntity v =
        versions
            .findByWorkflowIdAndVersion(id, w.getPublishedVersion())
            .orElseThrow(() -> new IllegalStateException("missing version row for " + id));
    return new PublishedDefinition(id, v.getVersion(), read(v.getDefinitionJson()));
  }

  // ---------------------------------------------------------------- commands

  /**
   * Creates a draft from a definition in the seed shape. Only the overall shape is enforced here;
   * catalog rules are enforced at publish (API_CONTRACT §1 allows either).
   *
   * @param body submitted definition
   * @param actor caller
   * @return the created workflow, with its validation report
   */
  @Transactional
  public WorkflowView create(JsonNode body, String actor) {
    ObjectNode def = requireObject(body);
    if (!def.hasNonNull("id")) {
      def.put("id", Ids.workflowId());
    }
    String id = requireId(def.get("id"));
    if (workflows.existsById(id)) {
      throw new ConflictException(
          ErrorCode.WORKFLOW_EXISTS, "Workflow '" + id + "' already exists", Map.of("id", id));
    }
    requireShape(def);
    DefinitionSecrets.Stripped stripped = DefinitionSecrets.strip(def);
    WorkflowEntity entity =
        new WorkflowEntity(
            id,
            def.path("name").asText(),
            textOrNull(def.get("description")),
            def.path("trigger").path("type").asText(),
            write(stripped.definition()),
            clock.instant());
    // Flush so the JDBC secret insert below can reference the row (FK workflow_secrets -> workflows).
    workflows.saveAndFlush(entity);
    stripped.secret().ifPresent(s -> secrets.put(id, s));
    audit.record(actor, "workflow.create", "workflow", id, Map.of());
    return detail(entity, WorkflowRunStatsQuery.Stats.NONE);
  }

  /**
   * Replaces the working copy. Published versions are never modified.
   *
   * @param id workflow id (path)
   * @param body new definition; its {@code id}, if present, must equal {@code id}
   * @param actor caller
   * @return the updated workflow
   */
  @Transactional
  public WorkflowView update(String id, JsonNode body, String actor) {
    WorkflowEntity entity = find(id);
    ObjectNode def = requireObject(body);
    if (def.hasNonNull("id") && !id.equals(def.get("id").asText())) {
      throw new BadRequestException(
          ErrorCode.INVALID_DEFINITION,
          "Definition id '" + def.get("id").asText() + "' does not match path id '" + id + "'");
    }
    def.put("id", id);
    requireShape(def);
    DefinitionSecrets.Stripped stripped = DefinitionSecrets.strip(def);
    entity.updateWorkingCopy(
        def.path("name").asText(),
        textOrNull(def.get("description")),
        def.path("trigger").path("type").asText(),
        write(stripped.definition()));
    stripped.secret().ifPresent(s -> secrets.put(id, s));
    audit.record(actor, "workflow.update", "workflow", id, Map.of());
    return detail(entity, runStats.all().getOrDefault(id, WorkflowRunStatsQuery.Stats.NONE));
  }

  /**
   * Validates the working copy against the catalog and freezes it as the next version.
   * Re-publishing an unchanged workflow is a no-op that returns the current version.
   *
   * @param id workflow id
   * @param actor caller
   * @return publish outcome
   * @throws DefinitionValidationException (422) on any blocking violation
   */
  @Transactional
  public PublishResult publish(String id, String actor) {
    WorkflowEntity entity = find(id);
    if ("published".equals(entity.getStatus()) && !entity.isHasUnpublishedChanges()) {
      return new PublishResult(
          detail(entity, WorkflowRunStatsQuery.Stats.NONE), entity.getPublishedVersion(), false);
    }
    JsonNode working = read(entity.getDefinitionJson());
    ValidationReport report =
        validator.validate(DefinitionSecrets.forValidation(working, secrets.has(id)));
    if (!report.isValid()) {
      throw new DefinitionValidationException(report);
    }
    int version = entity.getPublishedVersion() == null ? 1 : entity.getPublishedVersion() + 1;
    PayloadSigner.Signature sig = signer.sign(CanonicalJson.bytes(working));
    versions.save(
        new WorkflowVersionEntity(
            id,
            version,
            write(working),
            sig.bytes(),
            sig.keyId(),
            actor,
            clock.instant()));
    entity.markPublished(version);
    scheduleSync.onPublished(id, working);
    audit.record(
        actor,
        "workflow.publish",
        "workflow",
        id,
        Map.of("version", version, "warnings", report.warnings().size()));
    return new PublishResult(
        detail(entity, runStats.all().getOrDefault(id, WorkflowRunStatsQuery.Stats.NONE)),
        version,
        true);
  }

  /**
   * Dry-run validation used by the console's editor. Nothing is stored.
   *
   * @param body definition to validate
   * @return the report
   */
  public ValidationReport validate(JsonNode body) {
    return validator.validate(requireObject(body));
  }

  // ---------------------------------------------------------------- helpers

  private WorkflowEntity find(String id) {
    return workflows.findById(id).orElseThrow(() -> NotFoundException.workflow(id));
  }

  private ObjectNode requireObject(JsonNode body) {
    if (body == null || !body.isObject()) {
      throw new BadRequestException(
          ErrorCode.INVALID_REQUEST, "Request body must be a JSON object (a workflow definition)");
    }
    return ((ObjectNode) body).deepCopy();
  }

  private static String requireId(JsonNode idNode) {
    if (!idNode.isTextual() || !ID_PATTERN.matcher(idNode.asText()).matches()) {
      throw new BadRequestException(
          ErrorCode.INVALID_DEFINITION,
          "Workflow 'id' must be 1-128 characters of letters, digits, '_' or '-'");
    }
    return idNode.asText();
  }

  /** Create/update-time shape checks; everything else is publish-time (catalog) validation. */
  private void requireShape(ObjectNode def) {
    ValidationReport shape = validator.validateShape(def);
    if (!shape.isValid()) {
      var first = shape.errors().get(0);
      throw new BadRequestException(
          ErrorCode.INVALID_DEFINITION,
          first.message(),
          Map.of("violations", shape.errors().stream().map(v -> v.toMap()).toList()));
    }
  }

  private WorkflowView summary(WorkflowEntity w, WorkflowRunStatsQuery.Stats stats) {
    return new WorkflowView(
        w.getId(),
        w.getName(),
        w.getDescription(),
        WorkflowStatus.fromWire(w.getStatus()),
        w.getTriggerType(),
        w.getPublishedVersion(),
        w.isHasUnpublishedChanges(),
        w.getCreatedAt(),
        w.getUpdatedAt(),
        stats.runCount(),
        stats.lastRunAt(),
        stats.lastRunStatus(),
        "webhook".equals(w.getTriggerType()) ? "/hooks/" + w.getId() : null,
        null,
        null,
        null,
        null);
  }

  private WorkflowView detail(WorkflowEntity w, WorkflowRunStatsQuery.Stats stats) {
    JsonNode working = read(w.getDefinitionJson());
    List<WorkflowVersionEntity> history = versions.findAllByWorkflowIdOrderByVersionDesc(w.getId());
    JsonNode published =
        history.isEmpty() ? null : DefinitionSecrets.redactedView(read(history.get(0).getDefinitionJson()));
    List<WorkflowView.VersionView> versionViews =
        history.stream()
            .map(
                v ->
                    new WorkflowView.VersionView(
                        v.getVersion(),
                        v.getPublishedBy(),
                        v.getCreatedAt(),
                        "ML-DSA-65",
                        v.getSigningKeyId(),
                        v.getSignature() == null
                            ? null
                            : signer.verify(
                                CanonicalJson.bytes(read(v.getDefinitionJson())),
                                v.getSigningKeyId(),
                                v.getSignature())))
            .toList();
    ValidationReport report =
        validator.validate(DefinitionSecrets.forValidation(working, secrets.has(w.getId())));
    WorkflowView s = summary(w, stats);
    return new WorkflowView(
        s.id(),
        s.name(),
        s.description(),
        s.status(),
        s.triggerType(),
        s.publishedVersion(),
        s.hasUnpublishedChanges(),
        s.createdAt(),
        s.updatedAt(),
        s.runCount(),
        s.lastRunAt(),
        s.lastRunStatus(),
        s.webhookPath(),
        DefinitionSecrets.redactedView(working),
        published,
        versionViews,
        ValidationView.of(report));
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

  private static String textOrNull(JsonNode n) {
    return n == null || n.isNull() ? null : n.asText();
  }

  /**
   * A published definition ready to be snapshotted onto a run.
   *
   * @param workflowId workflow id
   * @param version version number
   * @param definition secret-free definition
   */
  public record PublishedDefinition(String workflowId, int version, JsonNode definition) {}

  /**
   * Outcome of a publish.
   *
   * @param workflow the workflow after publishing
   * @param version the (new or current) published version
   * @param created whether a new version was created
   */
  public record PublishResult(WorkflowView workflow, int version, boolean created) {}
}
