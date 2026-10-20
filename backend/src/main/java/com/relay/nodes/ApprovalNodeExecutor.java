package com.relay.nodes;

import org.springframework.stereotype.Component;

/**
 * {@code approval}: "Pauses the run in waiting_approval until a human approves (run resumes) or
 * rejects (run ends cancelled). The decision, decider, and timestamp are recorded and satisfy
 * requires_approval checks for the rest of the run." — node_catalog.json
 *
 * <p>Param (verbatim): {@code message} (required, templatable; "shown to the approver in the
 * console"). Output after the decision: {@code {"decision": string, "decided_by": string}} (written
 * by {@code ApprovalService}). Creating the approval row is the engine's job so it is idempotent
 * per step sequence across crashes.
 */
@Component
public class ApprovalNodeExecutor implements NodeExecutor {

  @Override
  public String type() {
    return "approval";
  }

  @Override
  public NodeResult execute(NodeExecutionContext ctx) {
    return NodeResult.awaitApproval(ctx.text("message", ""));
  }
}
