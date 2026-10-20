package com.relay.persistence.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.relay.domain.run.TriggerType;
import com.relay.engine.queue.WorkQueue;
import com.relay.persistence.entity.RunEntity;
import com.relay.persistence.entity.WorkflowEntity;
import com.relay.persistence.repository.RunRepository;
import com.relay.persistence.repository.WorkflowRepository;
import com.relay.testing.PostgresContainerSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The Postgres queue.
 *
 * <p>Satisfies: "Requirements: jobs survive restart, and a job picked up by a crashed worker
 * becomes available again (visibility timeout, lease, or heartbeat)." — DATA_MODEL, Queue
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcWorkQueue.class)
class JdbcWorkQueueIT extends PostgresContainerSupport {

  @Autowired JdbcWorkQueue queue;
  @Autowired JdbcTemplate jdbc;
  @Autowired WorkflowRepository workflows;
  @Autowired RunRepository runs;

  @BeforeEach
  void seed() {
    // The container is shared with other integration tests that commit queued runs.
    jdbc.update("DELETE FROM queue_jobs");
    workflows.saveAndFlush(new WorkflowEntity("wf_q", "q", null, "manual", "{}", Instant.now()));
    for (String id : List.of("run_a", "run_b", "run_c")) {
      runs.saveAndFlush(
          new RunEntity(
              id, "wf_q", 1, "{}", null, null, TriggerType.MANUAL, "{}", "n", "t-" + id, Instant.now()));
    }
  }

  @Test
  void enqueueIsIdempotentPerRun() {
    queue.enqueue("run_a", Instant.now(), "t");
    queue.enqueue("run_a", Instant.now(), "t");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM queue_jobs", Integer.class)).isEqualTo(1);
  }

  @Test
  void twoWorkersNeverClaimTheSameJob() {
    queue.enqueue("run_a", Instant.now().minusSeconds(120), "t");
    queue.enqueue("run_b", Instant.now().minusSeconds(120), "t");
    List<WorkQueue.Job> w1 = queue.claim("w1:x", 1, Duration.ofSeconds(30));
    List<WorkQueue.Job> w2 = queue.claim("w2:x", 5, Duration.ofSeconds(30));
    assertThat(w1).hasSize(1);
    assertThat(w2).hasSize(1);
    assertThat(w1.get(0).runId()).isNotEqualTo(w2.get(0).runId());
    assertThat(queue.claim("w3:x", 5, Duration.ofSeconds(30))).isEmpty();
    assertThat(w1.get(0).traceId()).isEqualTo("t");
    assertThat(w1.get(0).attempts()).isEqualTo(1);
  }

  @Test
  void futureJobsAreNotClaimable() {
    queue.enqueue("run_a", Instant.now().plusSeconds(60), "t");
    assertThat(queue.claim("w1:x", 5, Duration.ofSeconds(30))).isEmpty();
  }

  @Test
  void expiredLeaseIsReclaimedForTheNextWorker() {
    queue.enqueue("run_a", Instant.now().minusSeconds(120), "t");
    assertThat(queue.claim("dead:x", 1, Duration.ofSeconds(30))).hasSize(1);
    // simulate kill -9: the dead worker stops heart-beating and its lease runs out
    jdbc.update("UPDATE queue_jobs SET locked_until = now() - interval '1 second'");
    assertThat(queue.isLocked("run_a")).isFalse();
    assertThat(queue.reclaimExpired()).isEqualTo(1);
    List<WorkQueue.Job> reclaimed = queue.claim("fresh:y", 1, Duration.ofSeconds(30));
    assertThat(reclaimed).extracting(WorkQueue.Job::runId).containsExactly("run_a");
    assertThat(reclaimed.get(0).attempts()).isEqualTo(2);
  }

  @Test
  void heartbeatOnlyExtendsOwnLeases() {
    queue.enqueue("run_a", Instant.now().minusSeconds(120), "t");
    long id = queue.claim("w1:x", 1, Duration.ofSeconds(5)).get(0).id();
    assertThat(queue.heartbeat("w1:x", List.of(id), Duration.ofSeconds(30))).containsExactly(id);
    assertThat(queue.heartbeat("w2:x", List.of(id), Duration.ofSeconds(30))).isEmpty();
  }

  @Test
  void rescheduleReleasesUntilResumeAt() {
    queue.enqueue("run_a", Instant.now().minusSeconds(120), "t");
    long id = queue.claim("w1:x", 1, Duration.ofSeconds(30)).get(0).id();
    queue.reschedule(id, "w1:x", Instant.now().plusSeconds(20));
    assertThat(queue.isLocked("run_a")).isFalse();
    assertThat(queue.claim("w1:x", 1, Duration.ofSeconds(30))).isEmpty();
    jdbc.update("UPDATE queue_jobs SET available_at = now() - interval '1 second'");
    assertThat(queue.claim("w1:x", 1, Duration.ofSeconds(30))).hasSize(1);
  }

  @Test
  void completeDeletesOnlyForTheOwner() {
    queue.enqueue("run_a", Instant.now().minusSeconds(120), "t");
    long id = queue.claim("w1:x", 1, Duration.ofSeconds(30)).get(0).id();
    queue.complete(id, "someone-else");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM queue_jobs", Integer.class)).isEqualTo(1);
    queue.complete(id, "w1:x");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM queue_jobs", Integer.class)).isZero();
  }

  @Test
  void restartedStableWorkerReleasesItsPreviousIncarnation() {
    queue.enqueue("run_a", Instant.now().minusSeconds(120), "t");
    queue.enqueue("run_b", Instant.now().minusSeconds(120), "t");
    queue.claim("worker-1:old", 1, Duration.ofSeconds(300));
    queue.claim("worker-2:live", 1, Duration.ofSeconds(300));
    assertThat(queue.releasePreviousIncarnation("worker-1:", "worker-1:new")).isEqualTo(1);
    assertThat(queue.claim("worker-1:new", 5, Duration.ofSeconds(30))).hasSize(1);
  }

  @Test
  void cancelCanOnlyDeleteUnlockedJobs() {
    queue.enqueue("run_a", Instant.now().minusSeconds(120), "t");
    queue.enqueue("run_b", Instant.now().plusSeconds(60), "t");
    queue.claim("w1:x", 1, Duration.ofSeconds(30));
    assertThat(queue.deleteIfUnlocked("run_a")).isFalse();
    assertThat(queue.deleteIfUnlocked("run_b")).isTrue();
  }
}
