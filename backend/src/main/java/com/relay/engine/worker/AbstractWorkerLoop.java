package com.relay.engine.worker;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template Method for a polling worker: <b>acquire → execute (which persists) → acknowledge</b>.
 * Subclasses supply the steps; the skeleton owns the loop, idle back-off and error containment so
 * no single bad job can kill the worker thread.
 *
 * @param <J> job type
 * @param <O> outcome type
 */
public abstract class AbstractWorkerLoop<J, O> implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(AbstractWorkerLoop.class);

  private volatile boolean running = true;

  @Override
  public final void run() {
    onStart();
    while (running) {
      try {
        List<J> jobs = acquire();
        if (jobs.isEmpty()) {
          idle();
          continue;
        }
        for (J job : jobs) {
          dispatch(job);
        }
      } catch (RuntimeException e) {
        log.error("Worker loop iteration failed; backing off", e);
        idle();
      }
    }
    onStop();
  }

  /** Requests the loop to stop after the current iteration. */
  public void stop() {
    running = false;
  }

  /** True until {@link #stop()} is called. */
  public boolean isRunning() {
    return running;
  }

  /**
   * The template step for one job: execute, contain unexpected errors, acknowledge. Subclasses call
   * this from {@link #dispatch(Object)} (synchronously or on a pool thread).
   *
   * @param job the job
   */
  protected final void process(J job) {
    O outcome;
    try {
      outcome = execute(job);
    } catch (RuntimeException e) {
      outcome = onExecuteError(job, e);
    }
    acknowledge(job, outcome);
  }

  /** Called once before the loop starts (e.g. crash recovery). */
  protected void onStart() {}

  /** Called once after the loop exits. */
  protected void onStop() {}

  /** Claims work; returns an empty list when there is none. */
  protected abstract List<J> acquire();

  /** Hands a claimed job to {@link #process(Object)}. */
  protected abstract void dispatch(J job);

  /** Does the work (and persists its effects). */
  protected abstract O execute(J job);

  /** Converts an unexpected error into an outcome (e.g. reschedule with backoff). */
  protected abstract O onExecuteError(J job, RuntimeException e);

  /** Acknowledges the job according to the outcome. */
  protected abstract void acknowledge(J job, O outcome);

  /** Waits before polling again. */
  protected abstract void idle();
}
