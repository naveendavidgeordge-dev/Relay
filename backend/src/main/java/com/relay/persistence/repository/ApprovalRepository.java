package com.relay.persistence.repository;

import com.relay.persistence.entity.ApprovalEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@code approvals}. */
public interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {

  /** Approvals in a status, oldest first ({@code GET /approvals?status=pending}). */
  List<ApprovalEntity> findAllByStatusOrderByCreatedAtAsc(String status);

  /** Every approval, newest first (console history). */
  List<ApprovalEntity> findAllByOrderByCreatedAtDesc();

  /** Approvals of one run, in creation order. */
  List<ApprovalEntity> findAllByRunIdOrderByCreatedAtAsc(String runId);

  /** The approval created by a given approval step (idempotent re-creation on resume). */
  Optional<ApprovalEntity> findByRunIdAndStepSequence(String runId, int stepSequence);

  /**
   * The engine gate's query: approvals of this run in {@code status} whose step ran before {@code
   * sequence}. "Has this run an approved approval? is a query over Approval rows, checked by the
   * engine before executing any {@code requires_approval} node." — DATA_MODEL
   *
   * @param runId run id
   * @param status {@code approved}
   * @param sequence sequence of the gated step
   * @return matching approvals
   */
  List<ApprovalEntity> findAllByRunIdAndStatusAndStepSequenceLessThan(
      String runId, String status, int sequence);

  /** Loads an approval with a row lock (decisions are serialized). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from ApprovalEntity a where a.id = :id")
  Optional<ApprovalEntity> findForUpdate(@Param("id") String id);
}
