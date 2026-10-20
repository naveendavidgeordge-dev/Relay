package com.relay.persistence.repository;

import com.relay.persistence.entity.ScheduleEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@code schedules} (Good To Have cron triggers). */
public interface ScheduleRepository extends JpaRepository<ScheduleEntity, Long> {

  /** The schedule of a workflow, if any. */
  Optional<ScheduleEntity> findByWorkflowId(String workflowId);

  /**
   * Due schedules, locked so that concurrent workers never fire the same window twice.
   *
   * @param now current time
   * @param limit max rows
   * @return due schedules (locked until the transaction ends)
   */
  @Query(
      value =
          "SELECT * FROM schedules WHERE enabled AND next_fire_at <= :now"
              + " ORDER BY next_fire_at LIMIT :limit FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<ScheduleEntity> lockDue(@Param("now") Instant now, @Param("limit") int limit);
}
