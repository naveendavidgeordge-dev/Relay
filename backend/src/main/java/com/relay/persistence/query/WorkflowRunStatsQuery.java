package com.relay.persistence.query;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Per-workflow run counters for the workflow list (one query, no N+1). */
@Component
public class WorkflowRunStatsQuery {

  private static final String SQL =
      """
      SELECT s.workflow_id, s.run_count, s.last_run_at, l.status AS last_run_status
        FROM (SELECT workflow_id, count(*) AS run_count, max(created_at) AS last_run_at
                FROM runs GROUP BY workflow_id) s
        LEFT JOIN LATERAL (SELECT status FROM runs r
                            WHERE r.workflow_id = s.workflow_id
                            ORDER BY r.created_at DESC LIMIT 1) l ON TRUE
      """;

  private final JdbcTemplate jdbc;

  /**
   * Creates the query.
   *
   * @param jdbc JDBC template
   */
  public WorkflowRunStatsQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Stats keyed by workflow id; workflows with no runs are absent. */
  public Map<String, Stats> all() {
    Map<String, Stats> out = new HashMap<>();
    jdbc.query(
        SQL,
        rs -> {
          OffsetDateTime last = rs.getObject("last_run_at", OffsetDateTime.class);
          out.put(
              rs.getString("workflow_id"),
              new Stats(
                  rs.getLong("run_count"),
                  last == null ? null : last.toInstant(),
                  rs.getString("last_run_status")));
        });
    return out;
  }

  /**
   * Run stats for one workflow.
   *
   * @param runCount number of runs
   * @param lastRunAt latest run creation time
   * @param lastRunStatus latest run status
   */
  public record Stats(long runCount, Instant lastRunAt, String lastRunStatus) {
    /** Stats for a workflow that has never run. */
    public static final Stats NONE = new Stats(0, null, null);
  }
}
