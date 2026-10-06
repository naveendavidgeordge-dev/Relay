package com.relay.persistence.queue;

import com.relay.engine.queue.WorkQueue;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Postgres-backed {@link WorkQueue} ({@code queue_jobs}). See ADR-0002.
 *
 * <p>Claiming uses {@code SELECT … FOR UPDATE SKIP LOCKED} inside an {@code UPDATE … RETURNING},
 * so concurrent workers never block on or double-claim a job. Leases are timestamps compared with
 * the database clock ({@code now()}), so worker clock skew cannot expire a lease early.
 */
@Component
public class JdbcWorkQueue implements WorkQueue {

  private static final String CLAIM =
      """
      UPDATE queue_jobs
         SET locked_by = :worker,
             locked_until = now() + make_interval(secs => :lease),
             attempts = attempts + 1
       WHERE id IN (SELECT id FROM queue_jobs
                     WHERE locked_by IS NULL AND available_at <= now()
                     ORDER BY available_at, id
                     LIMIT :limit
                     FOR UPDATE SKIP LOCKED)
      RETURNING id, run_id, trace_id, attempts
      """;

  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate named;

  /**
   * Creates the queue.
   *
   * @param jdbc JDBC template
   * @param named named-parameter template
   */
  public JdbcWorkQueue(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
    this.jdbc = jdbc;
    this.named = named;
  }

  @Override
  public void enqueue(String runId, Instant availableAt, String traceId) {
    jdbc.update(
        """
        INSERT INTO queue_jobs (run_id, available_at, trace_id) VALUES (?, ?, ?)
        ON CONFLICT (run_id) DO UPDATE
           SET available_at = LEAST(queue_jobs.available_at, EXCLUDED.available_at)
        """,
        runId,
        ts(availableAt),
        traceId);
  }

  @Override
  public List<Job> claim(String workerId, int limit, Duration lease) {
    if (limit <= 0) {
      return List.of();
    }
    return named.query(
        CLAIM,
        Map.of("worker", workerId, "lease", (double) lease.toMillis() / 1000.0, "limit", limit),
        (rs, i) ->
            new Job(
                rs.getLong("id"),
                rs.getString("run_id"),
                rs.getString("trace_id"),
                rs.getInt("attempts")));
  }

  @Override
  public List<Long> heartbeat(String workerId, Collection<Long> jobIds, Duration lease) {
    if (jobIds.isEmpty()) {
      return List.of();
    }
    List<Long> extended =
        named.queryForList(
            """
            UPDATE queue_jobs SET locked_until = now() + make_interval(secs => :lease)
             WHERE locked_by = :worker AND id IN (:ids)
            RETURNING id
            """,
            Map.of("worker", workerId, "ids", jobIds, "lease", (double) lease.toMillis() / 1000.0),
            Long.class);
    named.update(
        """
        UPDATE runs SET lease_expires_at = now() + make_interval(secs => :lease)
         WHERE lease_owner = :worker
           AND run_id IN (SELECT run_id FROM queue_jobs WHERE id IN (:ids))
        """,
        Map.of("worker", workerId, "ids", jobIds, "lease", (double) lease.toMillis() / 1000.0));
    return extended;
  }

  @Override
  public void complete(long jobId, String workerId) {
    jdbc.update("DELETE FROM queue_jobs WHERE id = ? AND locked_by = ?", jobId, workerId);
  }

  @Override
  public void reschedule(long jobId, String workerId, Instant availableAt) {
    jdbc.update(
        "UPDATE queue_jobs SET locked_by = NULL, locked_until = NULL, available_at = ?"
            + " WHERE id = ? AND locked_by = ?",
        ts(availableAt),
        jobId,
        workerId);
  }

  @Override
  public int reclaimExpired() {
    return jdbc.update(
        "UPDATE queue_jobs SET locked_by = NULL, locked_until = NULL"
            + " WHERE locked_by IS NOT NULL AND locked_until < now()");
  }

  @Override
  public int releasePreviousIncarnation(String stableIdPrefix, String exceptWorkerId) {
    return jdbc.update(
        "UPDATE queue_jobs SET locked_by = NULL, locked_until = NULL"
            + " WHERE starts_with(locked_by, ?) AND locked_by <> ?",
        stableIdPrefix,
        exceptWorkerId);
  }

  @Override
  public boolean deleteIfUnlocked(String runId) {
    return jdbc.update("DELETE FROM queue_jobs WHERE run_id = ? AND locked_by IS NULL", runId) > 0;
  }

  @Override
  public boolean isLocked(String runId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM queue_jobs WHERE run_id = ? AND locked_by IS NOT NULL"
                + " AND locked_until >= now()",
            Integer.class,
            runId);
    return n != null && n > 0;
  }

  private static OffsetDateTime ts(Instant i) {
    return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
  }
}
