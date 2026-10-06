package com.relay.domain.run;

/** A transition that is not in the {@link RunStatus} table was attempted. */
public class IllegalRunTransitionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final RunStatus from;
  private final RunEvent event;

  /**
   * Creates the exception.
   *
   * @param from current state
   * @param event rejected event
   */
  public IllegalRunTransitionException(RunStatus from, RunEvent event) {
    super("Run in state '" + from.wire() + "' cannot accept " + event);
    this.from = from;
    this.event = event;
  }

  /** The state the run was in. */
  public RunStatus from() {
    return from;
  }

  /** The rejected event. */
  public RunEvent event() {
    return event;
  }
}
