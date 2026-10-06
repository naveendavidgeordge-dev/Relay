package com.relay.domain.run;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * The run state machine, implemented with the State pattern: each constant decides its own
 * successor for an event and rejects everything else.
 *
 * <p>Fixed contract: "The run status set: {@code queued}, {@code running}, {@code
 * waiting_approval}, {@code succeeded}, {@code failed}, {@code cancelled}." — API_CONTRACT §3. No
 * other value is ever persisted or returned.
 *
 * <pre>
 *   state             START    PAUSE      APPROVE  REJECT     SUCCEED    FAIL    CANCEL
 *   queued            running  -          -        -          -          failed  cancelled
 *   running           -        waiting    -        -          succeeded  failed  cancelled
 *   waiting_approval  -        -          running  cancelled  -          failed  cancelled
 *   succeeded/failed/cancelled: terminal — every event is rejected
 * </pre>
 */
public enum RunStatus {
  QUEUED {
    @Override
    public RunStatus on(RunEvent event) {
      return switch (event) {
        case START -> RUNNING;
        case FAIL -> FAILED;
        case CANCEL -> CANCELLED;
        default -> reject(event);
      };
    }
  },
  RUNNING {
    @Override
    public RunStatus on(RunEvent event) {
      return switch (event) {
        case PAUSE_FOR_APPROVAL -> WAITING_APPROVAL;
        case SUCCEED -> SUCCEEDED;
        case FAIL -> FAILED;
        case CANCEL -> CANCELLED;
        default -> reject(event);
      };
    }
  },
  WAITING_APPROVAL {
    @Override
    public RunStatus on(RunEvent event) {
      return switch (event) {
        case APPROVE -> RUNNING;
        case REJECT, CANCEL -> CANCELLED;
        case FAIL -> FAILED;
        default -> reject(event);
      };
    }
  },
  SUCCEEDED {
    @Override
    public RunStatus on(RunEvent event) {
      return reject(event);
    }
  },
  FAILED {
    @Override
    public RunStatus on(RunEvent event) {
      return reject(event);
    }
  },
  CANCELLED {
    @Override
    public RunStatus on(RunEvent event) {
      return reject(event);
    }
  };

  /**
   * The state reached from this one on {@code event}.
   *
   * @param event the event
   * @return the next state
   * @throws IllegalRunTransitionException if the transition is not in the table
   */
  public abstract RunStatus on(RunEvent event);

  /** True for {@code succeeded}, {@code failed}, {@code cancelled}. */
  public boolean isTerminal() {
    return this == SUCCEEDED || this == FAILED || this == CANCELLED;
  }

  /** True if {@code event} is allowed from this state. */
  public boolean accepts(RunEvent event) {
    try {
      on(event);
      return true;
    } catch (IllegalRunTransitionException e) {
      return false;
    }
  }

  /** Wire / column form, e.g. {@code waiting_approval}. */
  @JsonValue
  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * Parses the wire form.
   *
   * @param value e.g. {@code running}
   * @return the status
   */
  public static RunStatus fromWire(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }

  RunStatus reject(RunEvent event) {
    throw new IllegalRunTransitionException(this, event);
  }
}
