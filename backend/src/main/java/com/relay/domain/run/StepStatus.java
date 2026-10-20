package com.relay.domain.run;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Step status vocabulary ("{@code status} ({@code succeeded}, {@code failed}, {@code waiting},
 * ...)" — DATA_MODEL "Step").
 */
public enum StepStatus {
  /** Intent persisted; the node is executing (or was, when a worker died). */
  RUNNING,
  /** Output persisted; the run has advanced past it. */
  SUCCEEDED,
  /** Permanent failure (after retries, or non-retryable). */
  FAILED,
  /** Paused: an approval decision or a delay's {@code resume_at}. */
  WAITING,
  /** Closed by cancellation or rejection. */
  CANCELLED;

  /** Wire / column form. */
  @JsonValue
  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * Parses the wire form.
   *
   * @param value e.g. {@code waiting}
   * @return the status
   */
  public static StepStatus fromWire(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }

  /** True for {@code running} and {@code waiting}. */
  public boolean isInFlight() {
    return this == RUNNING || this == WAITING;
  }
}
