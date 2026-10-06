package com.relay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.application.WorkflowService;
import com.relay.application.view.ValidationView;
import com.relay.application.view.WorkflowView;
import com.relay.domain.error.BadRequestException;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.workflow.WorkflowStatus;
import com.relay.security.CurrentActor;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workflow definitions. Fixed contract routes (API_CONTRACT, Fixed Contract §2):
 *
 * <ul>
 *   <li>{@code GET /workflows} — "lists workflows with at least {@code id} and {@code status}".
 *   <li>{@code POST /workflows} — "accepts a definition in the seed shape, creates a Draft".
 *   <li>{@code POST /workflows/{workflowId}/publish} — "publishes, or rejects invalid definitions
 *       with 4xx".
 * </ul>
 *
 * Additional (documented in docs/api.md): {@code GET /workflows/{id}}, {@code PUT
 * /workflows/{id}}, {@code POST /workflows/validate}.
 */
@RestController
@RequestMapping("/workflows")
public class WorkflowController {

  private final WorkflowService workflows;

  /**
   * Creates the controller.
   *
   * @param workflows workflow service
   */
  public WorkflowController(WorkflowService workflows) {
    this.workflows = workflows;
  }

  /**
   * Lists workflows as a JSON array.
   *
   * @param status optional {@code draft} / {@code published} filter
   * @return workflows
   */
  @GetMapping
  public List<WorkflowView> list(@RequestParam(required = false) String status) {
    return workflows.list(parseStatus(status));
  }

  /**
   * Creates a draft.
   *
   * @param body definition in the seed shape
   * @return 201 with the workflow
   */
  @PostMapping
  @PreAuthorize("hasRole('BUILDER')")
  public ResponseEntity<WorkflowView> create(@RequestBody JsonNode body) {
    WorkflowView created = workflows.create(body, CurrentActor.name());
    return ResponseEntity.created(URI.create("/workflows/" + created.id())).body(created);
  }

  /**
   * Workflow detail.
   *
   * @param id workflow id
   * @return detail view
   */
  @GetMapping("/{id}")
  public WorkflowView get(@PathVariable String id) {
    return workflows.get(id);
  }

  /**
   * Replaces the working copy (published versions are immutable).
   *
   * @param id workflow id
   * @param body new definition
   * @return updated workflow
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('BUILDER')")
  public WorkflowView update(@PathVariable String id, @RequestBody JsonNode body) {
    return workflows.update(id, body, CurrentActor.name());
  }

  /**
   * Validates and freezes the working copy as a new version.
   *
   * @param id workflow id
   * @return the published workflow (422 on validation failure)
   */
  @PostMapping("/{id}/publish")
  @PreAuthorize("hasRole('BUILDER')")
  public WorkflowView publish(@PathVariable String id) {
    return workflows.publish(id, CurrentActor.name()).workflow();
  }

  /**
   * Dry-run validation for the console editor (nothing stored).
   *
   * @param body definition
   * @return validation report
   */
  @PostMapping("/validate")
  @PreAuthorize("hasRole('BUILDER')")
  public ValidationView validate(@RequestBody JsonNode body) {
    return ValidationView.of(workflows.validate(body));
  }

  private static Optional<WorkflowStatus> parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(WorkflowStatus.fromWire(status));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
          ErrorCode.INVALID_REQUEST, "status must be 'draft' or 'published'");
    }
  }
}
