package com.relay.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Row of {@code schedules} (Good To Have cron trigger). */
@Entity
@Table(name = "schedules")
public class ScheduleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "workflow_id", nullable = false, length = 128, unique = true)
  private String workflowId;

  @Column(name = "cron", nullable = false, length = 128)
  private String cron;

  @Column(name = "next_fire_at", nullable = false)
  private Instant nextFireAt;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "last_fired_at")
  private Instant lastFiredAt;

  @Column(name = "last_run_id", length = 64)
  private String lastRunId;

  protected ScheduleEntity() {}

  /**
   * Creates an enabled schedule.
   *
   * @param workflowId workflow id
   * @param cron 5-field cron
   * @param nextFireAt first fire time
   */
  public ScheduleEntity(String workflowId, String cron, Instant nextFireAt) {
    this.workflowId = workflowId;
    this.cron = cron;
    this.nextFireAt = nextFireAt;
    this.enabled = true;
  }

  /**
   * Re-points the schedule after a publish.
   *
   * @param newCron cron
   * @param next next fire time
   */
  public void reschedule(String newCron, Instant next) {
    this.cron = newCron;
    this.nextFireAt = next;
    this.enabled = true;
  }

  /**
   * Records a firing.
   *
   * @param firedAt fire time
   * @param runId created run
   * @param next next fire time (no catch-up for missed windows)
   */
  public void fired(Instant firedAt, String runId, Instant next) {
    this.lastFiredAt = firedAt;
    this.lastRunId = runId;
    this.nextFireAt = next;
  }

  /** Disables the schedule (workflow no longer schedule-triggered). */
  public void disable() {
    this.enabled = false;
  }

  public Long getId() {
    return id;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public String getCron() {
    return cron;
  }

  public Instant getNextFireAt() {
    return nextFireAt;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getLastFiredAt() {
    return lastFiredAt;
  }

  public String getLastRunId() {
    return lastRunId;
  }
}
