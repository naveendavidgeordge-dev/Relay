package com.relay.domain.run;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Approval status. {@code cancelled} closes a pending approval when its run is cancelled ("its
 * pending approval is closed (no longer actionable, not listed as pending)" — API_CONTRACT §5).
 */
public enum ApprovalStatus {
  PENDING,
  APPROVED,
  REJECTED,
  CANCELLED;

  /** Wire / column form. */
  @JsonValue
  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * Parses the wire form.
   *
   * @param value e.g. {@code pending}
   * @return the status
   */
  public static ApprovalStatus fromWire(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
