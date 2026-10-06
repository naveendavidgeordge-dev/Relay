package com.relay.api;

import com.relay.application.CancelService;
import com.relay.application.RunQueryService;
import com.relay.application.view.RunView;
import com.relay.domain.error.BadRequestException;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.run.RunStatus;
import com.relay.security.CurrentActor;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs and traces. {@code GET /runs/{runId}} is fixed contract ("returns at least {@code status}
 * and {@code steps[]}, each step with a {@code node_id} and {@code status}"); {@code GET /runs}
 * and {@code POST /runs/{id}/cancel} follow the suggested API surface (documented in
 * docs/api.md).
 */
@RestController
public class RunController {

  private final RunQueryService queries;
  private final CancelService cancels;

  /**
   * Creates the controller.
   *
   * @param queries read side
   * @param cancels cancellation
   */
  public RunController(RunQueryService queries, CancelService cancels) {
    this.queries = queries;
    this.cancels = cancels;
  }

  /**
   * Run history, newest first.
   *
   * @param workflowId filter (also accepts {@code workflowId})
   * @param workflowIdCamel alias of {@code workflow_id}
   * @param status filter
   * @param from ISO-8601 lower bound on {@code created_at}
   * @param to ISO-8601 upper bound
   * @param limit max rows (default 100)
   * @return runs without steps
   */
  @GetMapping("/runs")
  public List<RunView> list(
      @RequestParam(name = "workflow_id", required = false) String workflowId,
      @RequestParam(name = "workflowId", required = false) String workflowIdCamel,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "100") int limit) {
    return queries.list(
        Optional.ofNullable(workflowId != null ? workflowId : workflowIdCamel),
        Optional.ofNullable(status).filter(s -> !s.isBlank()).map(RunController::status),
        Optional.ofNullable(from).filter(s -> !s.isBlank()).map(RunController::instant),
        Optional.ofNullable(to).filter(s -> !s.isBlank()).map(RunController::instant),
        limit);
  }

  /**
   * Full trace.
   *
   * @param runId run id
   * @return run with steps
   */
  @GetMapping("/runs/{runId}")
  public RunView get(@PathVariable String runId) {
    return queries.get(runId);
  }

  /**
   * Cancels a run (409 if already terminal).
   *
   * @param runId run id
   * @return the run after the request
   */
  @PostMapping("/runs/{runId}/cancel")
  @PreAuthorize("hasRole('OPERATOR')")
  public RunView cancel(@PathVariable String runId) {
    return cancels.cancel(runId, CurrentActor.name());
  }

  private static RunStatus status(String s) {
    try {
      return RunStatus.fromWire(s);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
          ErrorCode.INVALID_REQUEST,
          "status must be one of queued, running, waiting_approval, succeeded, failed, cancelled");
    }
  }

  private static Instant instant(String s) {
    try {
      return Instant.parse(s);
    } catch (DateTimeParseException e) {
      throw new BadRequestException(ErrorCode.INVALID_REQUEST, "from/to must be ISO-8601 instants");
    }
  }
}
