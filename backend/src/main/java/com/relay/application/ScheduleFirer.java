package com.relay.application;

import com.relay.domain.schedule.CronSchedule;
import com.relay.persistence.entity.ScheduleEntity;
import com.relay.persistence.repository.ScheduleRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fires due schedules on worker processes. "1-minute granularity, no catch-up for missed windows.
 * A worker loop checking {@code next_fire_at <= now} every few seconds is fine." —
 * IMPLEMENTATION_GUIDE, FAQ. Due rows are locked with {@code SKIP LOCKED}, so several workers never
 * fire one window twice; after firing, {@code next_fire_at} jumps to the next window after
 * <em>now</em> (missed windows are skipped, not replayed).
 */
@Component
@ConditionalOnProperty(prefix = "relay.worker", name = "enabled", havingValue = "true")
public class ScheduleFirer {

  private static final Logger log = LoggerFactory.getLogger(ScheduleFirer.class);

  private final ScheduleRepository schedules;
  private final TriggerService triggers;
  private final Clock clock;

  /**
   * Creates the firer.
   *
   * @param schedules schedule repository
   * @param triggers trigger service
   * @param clock clock
   */
  public ScheduleFirer(ScheduleRepository schedules, TriggerService triggers, Clock clock) {
    this.schedules = schedules;
    this.triggers = triggers;
    this.clock = clock;
  }

  /** Polls every 10 s. */
  @Scheduled(fixedDelayString = "${relay.schedule.poll-ms:10000}", initialDelay = 5000)
  @Transactional
  public void fireDue() {
    Instant now = clock.instant();
    for (ScheduleEntity s : schedules.lockDue(now, 20)) {
      Instant next = CronSchedule.parse(s.getCron()).nextAfter(now);
      try {
        String runId = triggers.schedule(s.getWorkflowId()).runId();
        s.fired(now, runId, next);
        log.info("Schedule fired for {} -> {} (next {})", s.getWorkflowId(), runId, next);
      } catch (RuntimeException e) {
        s.fired(now, null, next);
        log.warn("Schedule for {} skipped: {}", s.getWorkflowId(), e.getMessage());
      }
    }
  }
}
