package com.relay.domain.workflow;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Workflow lifecycle: {@code draft} (editable) → {@code published} (frozen version, runnable).
 * DATA_MODEL "Workflow": {@code status (draft / published)}.
 */
public enum WorkflowStatus {
  DRAFT,
  PUBLISHED;

  /** Wire / column form. */
  @JsonValue
  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * Parses the wire form.
   *
   * @param value {@code draft} or {@code published} (case-insensitive)
   * @return the status
   */
  public static WorkflowStatus fromWire(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
