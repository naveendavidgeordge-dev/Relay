package com.relay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.application.ApprovalService;
import com.relay.application.RunQueryService;
import com.relay.application.view.ApprovalView;
import com.relay.domain.error.BadRequestException;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.run.ApprovalStatus;
import com.relay.security.CurrentActor;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Approvals. Fixed contract: {@code GET /approvals?status=pending} (array of {@code {id, run_id,
 * message, …}}) and {@code POST /approvals/{id}/approve}. {@code POST /approvals/{id}/reject} ends
 * the run {@code cancelled}. Optional body {@code {"comment": "..."}} on both decisions.
 */
@RestController
public class ApprovalController {

  private final ApprovalService approvals;
  private final RunQueryService queries;

  /**
   * Creates the controller.
   *
   * @param approvals decision service
   * @param queries read side
   */
  public ApprovalController(ApprovalService approvals, RunQueryService queries) {
    this.approvals = approvals;
    this.queries = queries;
  }

  /**
   * Lists approvals.
   *
   * @param status {@code pending} / {@code approved} / {@code rejected} / {@code cancelled}; all
   *     when absent
   * @return approvals
   */
  @GetMapping("/approvals")
  public List<ApprovalView> list(@RequestParam(required = false) String status) {
    Optional<String> s = Optional.ofNullable(status).filter(v -> !v.isBlank());
    s.ifPresent(ApprovalController::validate);
    return queries.approvals(s.map(v -> ApprovalStatus.fromWire(v).wire()));
  }

  /**
   * One approval.
   *
   * @param id approval id
   * @return the approval
   */
  @GetMapping("/approvals/{id}")
  public ApprovalView get(@PathVariable String id) {
    return queries.approval(id);
  }

  /**
   * Approves; the run resumes.
   *
   * @param id approval id
   * @param body optional {@code {"comment": "..."}}
   * @return the decided approval
   */
  @PostMapping("/approvals/{id}/approve")
  @PreAuthorize("hasRole('APPROVER')")
  public ApprovalView approve(
      @PathVariable String id, @RequestBody(required = false) JsonNode body) {
    return approvals.approve(id, CurrentActor.name(), comment(body));
  }

  /**
   * Rejects; the run ends {@code cancelled}.
   *
   * @param id approval id
   * @param body optional {@code {"comment": "..."}}
   * @return the decided approval
   */
  @PostMapping("/approvals/{id}/reject")
  @PreAuthorize("hasRole('APPROVER')")
  public ApprovalView reject(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
    return approvals.reject(id, CurrentActor.name(), comment(body));
  }

  private static String comment(JsonNode body) {
    if (body == null || !body.hasNonNull("comment")) {
      return null;
    }
    String c = body.get("comment").asText();
    return c.length() > 1000 ? c.substring(0, 1000) : c;
  }

  private static void validate(String status) {
    try {
      ApprovalStatus.fromWire(status);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
          ErrorCode.INVALID_REQUEST, "status must be pending, approved, rejected or cancelled");
    }
  }
}
