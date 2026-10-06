package com.relay.domain.run;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** How a run was started (DATA_MODEL "Run": {@code trigger_type}). */
public enum TriggerType {
  WEBHOOK,
  MANUAL,
  SCHEDULE;

  /** Wire / column form. */
  @JsonValue
  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * Parses the wire form.
   *
   * @param value e.g. {@code webhook}
   * @return the trigger type
   */
  public static TriggerType fromWire(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
