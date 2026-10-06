package com.relay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.application.TriggerService;
import com.relay.security.CurrentActor;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Run triggers (fixed contract, API_CONTRACT §2):
 *
 * <ul>
 *   <li>{@code POST /workflows/{workflowId}/trigger} — "body {@code {"input": {...}}}, returns a run
 *       id as {@code run_id}".
 *   <li>{@code POST /hooks/{workflowId}} — "secret checked from the {@code X-Relay-Secret} header;
 *       wrong/missing secret rejected 401/403; returns a run id".
 * </ul>
 *
 * Both answer {@code 202 Accepted} after enqueueing; nothing executes on the request thread.
 */
@RestController
public class TriggerController {

  /** The webhook secret header (fixed contract). */
  public static final String SECRET_HEADER = "X-Relay-Secret";

  private final TriggerService triggers;

  /**
   * Creates the controller.
   *
   * @param triggers trigger service
   */
  public TriggerController(TriggerService triggers) {
    this.triggers = triggers;
  }

  /**
   * Manual trigger.
   *
   * @param id workflow id
   * @param body {@code {"input": {...}}} (optional)
   * @return 202 with {@code run_id}
   */
  @PostMapping("/workflows/{id}/trigger")
  @PreAuthorize("hasAnyRole('BUILDER','OPERATOR')")
  public ResponseEntity<TriggerService.TriggerResult> trigger(
      @PathVariable String id, @RequestBody(required = false) JsonNode body) {
    return accepted(triggers.manual(id, body, CurrentActor.name()));
  }

  /**
   * Webhook trigger.
   *
   * @param id workflow id
   * @param secret {@code X-Relay-Secret}
   * @param body payload; becomes {@code {{trigger.body}}}
   * @return 202 with {@code run_id}
   */
  @PostMapping("/hooks/{id}")
  public ResponseEntity<TriggerService.TriggerResult> hook(
      @PathVariable String id,
      @RequestHeader(value = SECRET_HEADER, required = false) String secret,
      @RequestBody(required = false) JsonNode body) {
    return accepted(triggers.webhook(id, secret, body));
  }

  private static ResponseEntity<TriggerService.TriggerResult> accepted(
      TriggerService.TriggerResult r) {
    return ResponseEntity.accepted().location(URI.create("/runs/" + r.runId())).body(r);
  }
}
