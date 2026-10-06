package com.relay.application.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.application.WorkflowService;
import com.relay.application.view.ValidationView;
import com.relay.domain.error.BadRequestException;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.PublishValidator;
import com.relay.domain.validation.ValidationReport;
import com.relay.domain.workflow.DefinitionSecrets;
import com.relay.persistence.repository.WorkflowRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The compiler's referee. Whatever strategy proposed a workflow, this service enforces the rules
 * before anything is stored: catalog-only types (else refuse), approval gates before sensitive
 * nodes (inserted and reported), and the same publish validator the API uses. A valid draft is
 * persisted as a Draft workflow; its webhook secret is never echoed back.
 *
 * <p>Strategy is {@code relay.compiler.mode}: {@code heuristic} (default — deterministic, no model)
 * or {@code llm} (AI sidecar with one repair, falling back to heuristic if the provider fails).
 */
@Service
public class CompilerService {

  private static final Logger log = LoggerFactory.getLogger(CompilerService.class);

  private final HeuristicWorkflowCompiler heuristic;
  private final LlmWorkflowCompiler llm;
  private final CompilerGuardrails guardrails;
  private final PublishValidator validator;
  private final WorkflowService workflows;
  private final WorkflowRepository workflowRepo;
  private final String mode;

  /**
   * Creates the service.
   *
   * @param heuristic heuristic strategy
   * @param llm LLM strategy
   * @param guardrails guardrails
   * @param validator publish validator
   * @param workflows workflow service
   * @param workflowRepo workflow repository
   * @param mode {@code heuristic} or {@code llm}
   */
  public CompilerService(
      HeuristicWorkflowCompiler heuristic,
      LlmWorkflowCompiler llm,
      CompilerGuardrails guardrails,
      PublishValidator validator,
      WorkflowService workflows,
      WorkflowRepository workflowRepo,
      @Value("${relay.compiler.mode:heuristic}") String mode) {
    this.heuristic = heuristic;
    this.llm = llm;
    this.guardrails = guardrails;
    this.validator = validator;
    this.workflows = workflows;
    this.workflowRepo = workflowRepo;
    this.mode = mode;
  }

  /**
   * Compiles a description.
   *
   * @param description plain English
   * @param persist store a valid result as a Draft workflow
   * @param actor caller
   * @return the result (draft or refusal)
   */
  public Result compile(String description, boolean persist, String actor) {
    if (description == null || description.isBlank() || description.length() > 4000) {
      throw new BadRequestException(
          ErrorCode.INVALID_REQUEST, "description must be 1-4000 characters of plain English");
    }
    List<String> notes = new ArrayList<>();
    WorkflowCompiler.Proposal p;
    String used;
    if ("llm".equalsIgnoreCase(mode)) {
      try {
        p = llm.compile(description);
        used = llm.name();
      } catch (RuntimeException e) {
        log.warn("LLM compiler unavailable ({}); falling back to heuristic", e.getMessage());
        notes.add("LLM compiler unavailable; used the deterministic heuristic compiler instead.");
        p = heuristic.compile(description);
        used = heuristic.name();
      }
    } else {
      p = heuristic.compile(description);
      used = heuristic.name();
    }
    notes.addAll(p.notes());

    if (p.refusal() != null) {
      return Result.refused(p.refusal(), used, notes);
    }
    ObjectNode def = p.definition();
    Set<String> invented = guardrails.inventedTypes(def);
    if (!invented.isEmpty()) {
      return Result.refused(
          new WorkflowCompiler.Refusal(
              "The compiler proposed node types outside the catalog " + invented
                  + "; refusing rather than guessing.",
              List.copyOf(invented)),
          used,
          notes);
    }
    notes.addAll(guardrails.ensureApprovalGates(def));
    ValidationReport report = validator.validate(def);

    String workflowId = null;
    if (persist && report.isValid()) {
      String base = def.path("id").asText();
      String id = base;
      for (int i = 2; workflowRepo.existsById(id); i++) {
        id = base + "_" + i;
      }
      def.put("id", id);
      workflowId = workflows.create(def, actor).id();
    }
    return new Result(
        DefinitionSecrets.redactedView(def),
        def,
        workflowId,
        ValidationView.of(report),
        p.explanation(),
        notes,
        used,
        null);
  }

  /**
   * Compile result. {@code definition} (with the real secret) is internal and never serialized.
   *
   * @param workflow redacted definition for display
   * @param definition full definition (internal)
   * @param workflowId id of the stored draft, if persisted
   * @param validation publish-validation report
   * @param explanation design explanation
   * @param notes assumptions and guardrail actions
   * @param compiler strategy used
   * @param refusal refusal, when refused
   */
  public record Result(
      JsonNode workflow,
      @com.fasterxml.jackson.annotation.JsonIgnore JsonNode definition,
      String workflowId,
      ValidationView validation,
      String explanation,
      List<String> notes,
      String compiler,
      RefusalView refusal) {

    static Result refused(WorkflowCompiler.Refusal r, String compiler, List<String> notes) {
      return new Result(
          null, null, null, null, null, notes, compiler,
          new RefusalView(r.reason(), r.missingCapabilities()));
    }
  }

  /**
   * Refusal as returned by the API (API_CONTRACT §6 shape).
   *
   * @param reason explanation
   * @param missingCapabilities missing capabilities
   */
  public record RefusalView(String reason, List<String> missingCapabilities) {}
}
