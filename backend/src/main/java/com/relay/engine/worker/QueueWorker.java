package com.relay.engine.worker;

import com.relay.config.RelayProperties;
import com.relay.engine.JobOutcome;
import com.relay.engine.RunExecutor;
import com.relay.engine.queue.WorkQueue;
import com.relay.support.Ids;
import com.relay.support.TraceContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * The queue worker: claims run jobs with {@code SKIP LOCKED}, advances each run on a bounded pool,
 * renews leases with a heartbeat, and acknowledges according to the {@link JobOutcome}.
 *
 * <p>Crash recovery: a {@code kill -9}'d worker stops heart-beating, its leases expire, and
 * {@link WorkQueue#reclaimExpired()} (run on startup and on every poll) makes the jobs claimable
 * again. The next worker resumes each run from its persisted steps. With a stable {@code
 * RELAY_WORKER_ID} the restarted worker reclaims its own previous leases immediately.
 */
public class QueueWorker extends AbstractWorkerLoop<WorkQueue.Job, JobOutcome> {

  private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);
  private static final int POISON_THRESHOLD = 10;

  private final WorkQueue queue;
  private final RunExecutor executor;
  private final Clock clock;
  private final String workerId;
  private final String stableId;
  private final int concurrency;
  private final Duration lease;
  private final long pollIntervalMs;
  private final ExecutorService pool;
  private final ScheduledExecutorService heartbeat;
  private final Map<Long, AtomicBoolean> held = new ConcurrentHashMap<>();

  /**
   * Creates the worker.
   *
   * @param queue work queue
   * @param executor run executor
   * @param props relay properties
   * @param clock clock
   */
  public QueueWorker(WorkQueue queue, RunExecutor executor, RelayProperties props, Clock clock) {
    this.queue = queue;
    this.executor = executor;
    this.clock = clock;
    RelayProperties.Worker w = props.worker();
    this.stableId = w.id() == null || w.id().isBlank() ? null : w.id().strip();
    this.workerId = (stableId != null ? stableId : hostname()) + ":" + Ids.ulid().substring(16);
    this.concurrency = Math.max(1, w.concurrency());
    this.lease = Duration.ofSeconds(Math.max(3, w.leaseSeconds()));
    this.pollIntervalMs = Math.max(50, w.pollIntervalMs());
    this.pool = Executors.newFixedThreadPool(concurrency, r -> named(r, "relay-run"));
    this.heartbeat = Executors.newSingleThreadScheduledExecutor(r -> named(r, "relay-heartbeat"));
  }

  /** This worker incarnation's id ({@code <stable-or-host>:<boot>}). */
  public String workerId() {
    return workerId;
  }

  @Override
  protected void onStart() {
    MDC.put(TraceContext.WORKER_ID, workerId);
    if (stableId != null) {
      int mine = queue.releasePreviousIncarnation(stableId + ":", workerId);
      if (mine > 0) {
        log.info("Released {} job(s) held by this worker's previous incarnation", mine);
      }
    }
    int reclaimed = queue.reclaimExpired();
    log.info(
        "Worker {} started (concurrency={}, lease={}s); reclaimed {} expired lease(s)",
        workerId,
        concurrency,
        lease.toSeconds(),
        reclaimed);
    long beat = Math.max(1000, lease.toMillis() / 3);
    heartbeat.scheduleAtFixedRate(this::beat, beat, beat, TimeUnit.MILLISECONDS);
  }

  @Override
  protected void onStop() {
    heartbeat.shutdownNow();
    pool.shutdown();
    try {
      if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
        pool.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    log.info("Worker {} stopped", workerId);
  }

  @Override
  protected List<WorkQueue.Job> acquire() {
    int free = concurrency - held.size();
    if (free <= 0) {
      return List.of();
    }
    queue.reclaimExpired();
    return queue.claim(workerId, free, lease);
  }

  @Override
  protected void dispatch(WorkQueue.Job job) {
    held.put(job.id(), new AtomicBoolean(false));
    pool.execute(
        () -> {
          try (TraceContext.Scope ignored = TraceContext.bindRun(job.traceId(), job.runId())) {
            MDC.put(TraceContext.WORKER_ID, workerId);
            process(job);
          } finally {
            held.remove(job.id());
          }
        });
  }

  @Override
  protected JobOutcome execute(WorkQueue.Job job) {
    AtomicBoolean lost = held.get(job.id());
    return executor.advance(
        job.runId(), workerId, clock.instant().plus(lease), () -> lost != null && lost.get());
  }

  @Override
  protected JobOutcome onExecuteError(WorkQueue.Job job, RuntimeException e) {
    log.error("Engine error advancing run {} (claim #{})", job.runId(), job.attempts(), e);
    if (job.attempts() >= POISON_THRESHOLD) {
      executor.failPoisoned(
          job.runId(),
          "Engine failed to advance this run " + job.attempts() + " times; giving up ("
              + e.getClass().getSimpleName() + ")");
      return JobOutcome.COMPLETE;
    }
    long backoffMs = Math.min(60_000L, 500L * (1L << Math.min(job.attempts(), 7)));
    return JobOutcome.rescheduleAt(Instant.now(clock).plusMillis(backoffMs));
  }

  @Override
  protected void acknowledge(WorkQueue.Job job, JobOutcome outcome) {
    switch (outcome) {
      case JobOutcome.Complete c -> queue.complete(job.id(), workerId);
      case JobOutcome.Reschedule r -> queue.reschedule(job.id(), workerId, r.at());
      case JobOutcome.Abandon a -> log.warn("Abandoned job {} (lease lost)", job.id());
    }
  }

  @Override
  protected void idle() {
    try {
      Thread.sleep(pollIntervalMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      stop();
    }
  }

  private void beat() {
    try {
      Set<Long> ids = new HashSet<>(held.keySet());
      if (ids.isEmpty()) {
        return;
      }
      Set<Long> extended = new HashSet<>(queue.heartbeat(workerId, ids, lease));
      for (Long id : ids) {
        if (!extended.contains(id)) {
          AtomicBoolean flag = held.get(id);
          if (flag != null && !flag.getAndSet(true)) {
            log.warn("Lost lease on job {}; another worker may now own it", id);
          }
        }
      }
    } catch (RuntimeException e) {
      log.warn("Heartbeat failed: {}", e.getMessage());
    }
  }

  private static Thread named(Runnable r, String prefix) {
    Thread t = new Thread(r, prefix + "-" + Ids.ulid().substring(20));
    t.setDaemon(true);
    return t;
  }

  private static String hostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "worker";
    }
  }
}
