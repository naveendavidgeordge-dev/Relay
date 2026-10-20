package com.relay.engine.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Port for the durable run queue. One job per run that has work to do; a run is advanced only by
 * the worker holding its job's lease. Adapter: {@code persistence.queue.JdbcWorkQueue}.
 */
public interface WorkQueue {

  /**
   * Enqueues (or pulls forward) the job for a run. Idempotent per run.
   *
   * @param runId run id
   * @param availableAt earliest time a worker may claim it
   * @param traceId trace id to restore in the worker
   */
  void enqueue(String runId, Instant availableAt, String traceId);

  /**
   * Claims up to {@code limit} ready jobs with {@code FOR UPDATE SKIP LOCKED}.
   *
   * @param workerId claiming worker
   * @param limit max jobs
   * @param lease lease length
   * @return claimed jobs
   */
  List<Job> claim(String workerId, int limit, Duration lease);

  /**
   * Extends the leases of jobs this worker still holds.
   *
   * @param workerId worker id
   * @param jobIds held job ids
   * @param lease new lease length from now
   * @return the ids whose lease was actually extended (others were lost)
   */
  List<Long> heartbeat(String workerId, Collection<Long> jobIds, Duration lease);

  /**
   * Deletes a finished job (run terminal or waiting for a human).
   *
   * @param jobId job id
   * @param workerId owner
   */
  void complete(long jobId, String workerId);

  /**
   * Releases the lease and makes the job claimable again at {@code availableAt} (delays, backoff).
   *
   * @param jobId job id
   * @param workerId owner
   * @param availableAt next eligible time
   */
  void reschedule(long jobId, String workerId, Instant availableAt);

  /**
   * Frees jobs whose lease expired — the crashed-worker path. Called at worker startup and on
   * every poll.
   *
   * @return number of jobs reclaimed
   */
  int reclaimExpired();

  /**
   * Releases jobs locked by a previous incarnation of a worker with a stable identity (worker ids
   * are {@code <stable-id>:<boot-uuid>}). Safe only because a stable id is never shared by two
   * live workers.
   *
   * @param stableIdPrefix e.g. {@code worker-1:}
   * @param exceptWorkerId the current incarnation's full id
   * @return number of jobs released
   */
  int releasePreviousIncarnation(String stableIdPrefix, String exceptWorkerId);

  /**
   * Deletes a run's job only if no worker holds it (used by cancel on queued/sleeping runs).
   *
   * @param runId run id
   * @return true if an unlocked job was deleted
   */
  boolean deleteIfUnlocked(String runId);

  /**
   * Whether a worker currently holds the run's job.
   *
   * @param runId run id
   * @return true if locked
   */
  boolean isLocked(String runId);

  /**
   * A claimed job.
   *
   * @param id job id
   * @param runId run id
   * @param traceId trace id
   * @param attempts claim count (including this one)
   */
  record Job(long id, String runId, String traceId, int attempts) {}
}
