package com.relay.application.view;

import com.relay.domain.validation.ValidationReport;
import com.relay.domain.validation.Violation;
import java.util.List;

/**
 * Validation report as returned by the API.
 *
 * @param valid true if there are no blocking errors
 * @param errors blocking violations
 * @param warnings non-blocking findings
 */
public record ValidationView(boolean valid, List<ViolationView> errors, List<ViolationView> warnings) {

  /**
   * One violation.
   *
   * @param code error code, e.g. {@code invalid_node_type}
   * @param message human message
   * @param nodeId offending node, if any
   * @param field offending field, if any
   */
  public record ViolationView(String code, String message, String nodeId, String field) {
    static ViolationView of(Violation v) {
      return new ViolationView(v.code().code(), v.message(), v.nodeId(), v.field());
    }
  }

  /**
   * Maps a domain report.
   *
   * @param report domain report
   * @return the view
   */
  public static ValidationView of(ValidationReport report) {
    return new ValidationView(
        report.isValid(),
        report.errors().stream().map(ViolationView::of).toList(),
        report.warnings().stream().map(ViolationView::of).toList());
  }
}
