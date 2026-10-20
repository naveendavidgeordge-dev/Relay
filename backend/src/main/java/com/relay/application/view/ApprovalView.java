package com.relay.application.view;

import java.time.Instant;

/**
 * An approval. Fixed contract: "{@code GET /approvals?status=pending} → lists pending approvals
 * with at least {@code id} and {@code run_id}" (API_CONTRACT §2). "Record who decided and when"
 * (§4).
 *
 * @param id approval id
 * @param runId run id
 * @param workflowId workflow id
 * @param nodeId approval node id
 * @param stepSequence sequence of the approval step
 * @param message resolved message
 * @param status pending / approved / rejected / cancelled
 * @param createdAt creation time
 * @param decidedBy decider
 * @param decidedAt decision time
 * @param comment optional decision comment
 * @param signatureValid ML-DSA decision signature verifies (null while pending)
 */
public record ApprovalView(
    String id,
    String runId,
    String workflowId,
    String nodeId,
    int stepSequence,
    String message,
    String status,
    Instant createdAt,
    String decidedBy,
    Instant decidedAt,
    String comment,
    Boolean signatureValid) {}
