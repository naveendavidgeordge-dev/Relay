package com.relay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Append-only audit log ({@code audit_log}). Records who did what to which entity; joins the
 * caller's transaction so an action and its audit entry commit together. Never pass secrets in
 * {@code meta}.
 */
@Component
public class AuditTrail {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Clock clock;

  /**
   * Creates the audit trail.
   *
   * @param jdbc JDBC template
   * @param mapper Jackson mapper
   * @param clock clock
   */
  public AuditTrail(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
  }

  /**
   * Appends an entry.
   *
   * @param actor who acted (e.g. {@code demo}, {@code system:seed}, {@code webhook})
   * @param action verb, e.g. {@code workflow.publish}
   * @param entityType {@code workflow}, {@code run}, {@code approval}
   * @param entityId entity id
   * @param meta small, secret-free context
   */
  public void record(
      String actor, String action, String entityType, String entityId, Map<String, ?> meta) {
    String json;
    try {
      json = mapper.writeValueAsString(meta == null ? Map.of() : meta);
    } catch (JsonProcessingException e) {
      json = "{}";
    }
    Instant now = clock.instant();
    jdbc.update(
        "INSERT INTO audit_log (actor, action, entity_type, entity_id, at, meta_json)"
            + " VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))",
        actor,
        action,
        entityType,
        entityId,
        OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
        json);
  }
}
