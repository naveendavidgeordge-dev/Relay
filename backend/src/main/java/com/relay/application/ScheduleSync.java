package com.relay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.schedule.CronSchedule;
import com.relay.persistence.entity.ScheduleEntity;
import com.relay.persistence.repository.ScheduleRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code schedules} in step with published definitions (Good To Have cron trigger). Called
 * inside the publish transaction: a published {@code schedule} trigger upserts its row; any other
 * trigger type disables an existing one.
 */
@Component
public class ScheduleSync {

  private final ScheduleRepository schedules;
  private final Clock clock;

  /**
   * Creates the sync.
   *
   * @param schedules schedule repository
   * @param clock clock
   */
  public ScheduleSync(ScheduleRepository schedules, Clock clock) {
    this.schedules = schedules;
    this.clock = clock;
  }

  /**
   * Applies a newly published definition.
   *
   * @param workflowId workflow id
   * @param definition published definition
   */
  public void onPublished(String workflowId, JsonNode definition) {
    JsonNode trigger = definition.path("trigger");
    if ("schedule".equals(trigger.path("type").asText())) {
      String cron = trigger.path("cron").asText();
      var next = CronSchedule.parse(cron).nextAfter(clock.instant());
      ScheduleEntity s =
          schedules.findByWorkflowId(workflowId).orElseGet(() -> new ScheduleEntity(workflowId, cron, next));
      s.reschedule(cron, next);
      schedules.save(s);
    } else {
      schedules.findByWorkflowId(workflowId).ifPresent(ScheduleEntity::disable);
    }
  }
}
