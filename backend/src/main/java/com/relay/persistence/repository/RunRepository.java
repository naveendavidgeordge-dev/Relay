package com.relay.persistence.repository;

import com.relay.persistence.entity.RunEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@code runs}. */
public interface RunRepository
    extends JpaRepository<RunEntity, String>, JpaSpecificationExecutor<RunEntity> {

  /**
   * Loads a run with a row lock ({@code SELECT … FOR UPDATE}). Every state transition — by the
   * worker, the approvals API or the cancel API — happens under this lock, so transitions are
   * atomic ("Run status transitions should be atomic" — DATA_MODEL, Storage Expectations).
   *
   * @param runId run id
   * @return the locked run
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RunEntity r where r.runId = :runId")
  Optional<RunEntity> findForUpdate(@Param("runId") String runId);
}
