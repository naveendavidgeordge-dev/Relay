package com.relay.domain.run;

/** Events that move a run between {@link RunStatus} states. */
public enum RunEvent {
  /** A worker picked up a queued run. */
  START,
  /** An approval node was reached. */
  PAUSE_FOR_APPROVAL,
  /** The pending approval was approved. */
  APPROVE,
  /** The pending approval was rejected ("reject API ends it cleanly"). */
  REJECT,
  /** The last node completed. */
  SUCCEED,
  /** A step failed permanently, or a cap was exceeded. */
  FAIL,
  /** {@code POST /runs/{id}/cancel}. */
  CANCEL
}
