package com.relay.engine.worker;

import com.relay.config.RelayProperties;
import com.relay.engine.RunExecutor;
import com.relay.engine.queue.WorkQueue;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Starts the {@link QueueWorker} thread once the context is ready and stops it gracefully on
 * shutdown. Active only where {@code relay.worker.enabled=true} (the {@code worker} profile, or
 * the all-in-one dev mode) — the {@code api} profile never executes nodes.
 *
 * <p>"Do not execute runs inside the HTTP request handler. Triggers enqueue; the worker executes."
 * — Case Study, Avoid These Mistakes
 */
@Component
@ConditionalOnProperty(prefix = "relay.worker", name = "enabled", havingValue = "true")
public class WorkerLifecycle implements SmartLifecycle {

  private final QueueWorker worker;
  private Thread thread;
  private volatile boolean running;

  /**
   * Creates the lifecycle.
   *
   * @param queue work queue
   * @param executor run executor
   * @param props relay properties
   * @param clock clock
   */
  public WorkerLifecycle(
      WorkQueue queue, RunExecutor executor, RelayProperties props, Clock clock) {
    this.worker = new QueueWorker(queue, executor, props, clock);
  }

  @Override
  public void start() {
    thread = new Thread(worker, "relay-worker-loop");
    thread.setDaemon(false);
    thread.start();
    running = true;
  }

  @Override
  public void stop() {
    worker.stop();
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join(15_000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    // Start after everything else (web server, seeders); stop first.
    return Integer.MAX_VALUE - 100;
  }

  /** The worker (for tests and the health endpoint). */
  public QueueWorker worker() {
    return worker;
  }
}
