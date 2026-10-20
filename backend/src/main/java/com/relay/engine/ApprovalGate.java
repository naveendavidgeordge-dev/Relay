package com.relay.engine;

import com.relay.domain.run.ApprovalStatus;
import com.relay.persistence.entity.ApprovalEntity;
import com.relay.persistence.repository.ApprovalRepository;
import com.relay.security.crypto.PayloadSigner;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The engine-side approval gate. Before executing any node whose catalog entry says {@code
 * requires_approval: true}, the engine asks this gate — a database query, not a prompt.
 *
 * <p>"Approval gate as data. 'Has this run an approved approval?' is a query over Approval rows,
 * checked by the engine before executing any {@code requires_approval} node. Nothing the AI
 * outputs can write an Approval row." — DATA_MODEL, Correctness Requirements
 *
 * <p>An approval counts only if it belongs to this run, was decided {@code approved}, its approval
 * step ran <em>before</em> the gated step, and its ML-DSA decision signature verifies (a row
 * edited directly in the database does not open the gate).
 */
@Component
public class ApprovalGate {

  private final ApprovalRepository approvals;
  private final ApprovalSignatures signatures;

  /**
   * Creates the gate.
   *
   * @param approvals approval repository
   * @param signatures decision signature helper
   */
  public ApprovalGate(ApprovalRepository approvals, ApprovalSignatures signatures) {
    this.approvals = approvals;
    this.signatures = signatures;
  }

  /**
   * Finds the approval that authorizes a gated step.
   *
   * @param runId run id
   * @param gatedSequence sequence of the step about to execute
   * @return the authorizing approval, or empty (the engine must refuse)
   */
  public Optional<ApprovalEntity> authorizing(String runId, int gatedSequence) {
    List<ApprovalEntity> approved =
        approvals.findAllByRunIdAndStatusAndStepSequenceLessThan(
            runId, ApprovalStatus.APPROVED.wire(), gatedSequence);
    return approved.stream().filter(signatures::verify).reduce((a, b) -> b);
  }

  /** Convenience wrapper over {@link PayloadSigner} for approval decisions. */
  public interface ApprovalSignatures {
    /**
     * Whether an approval's decision signature verifies.
     *
     * @param approval approval row
     * @return true if valid
     */
    boolean verify(ApprovalEntity approval);
  }
}
