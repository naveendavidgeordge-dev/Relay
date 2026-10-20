package com.relay.engine;

import java.time.Instant;

/** What the worker should do with a run's queue job after advancing it. */
public sealed interface JobOutcome
    permits JobOutcome.Complete, JobOutcome.Reschedule, JobOutcome.Abandon {

  /** Delete the job: the run is terminal or waiting for a human decision. */
  record Complete() implements JobOutcome {}

  /**
   * Release the job until {@code at} (a delay's {@code resume_at}, or backoff after an engine
   * error).
   *
   * @param at next eligible time
   */
  record Reschedule(Instant at) implements JobOutcome {}

  /** The lease was lost to another worker: touch nothing. */
  record Abandon() implements JobOutcome {}

  /** Singleton complete outcome. */
  JobOutcome COMPLETE = new Complete();

  /** Singleton abandon outcome. */
  JobOutcome ABANDON = new Abandon();

  /** A reschedule outcome. */
  static JobOutcome rescheduleAt(Instant at) {
    return new Reschedule(at);
  }
}
