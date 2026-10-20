package com.relay.domain.error;

import com.relay.domain.validation.ValidationReport;
import com.relay.domain.validation.Violation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publish-time validation failed (422). The top-level code and message are those of the first
 * blocking violation; {@code details.violations} lists all of them, plus any warnings.
 */
public final class DefinitionValidationException extends RelayException {

  private final transient ValidationReport report;

  /**
   * Creates the exception from a report that has at least one error.
   *
   * @param report the validation report
   */
  public DefinitionValidationException(ValidationReport report) {
    super(first(report).code(), first(report).message(), details(report));
    this.report = report;
  }

  /** The full report. */
  public ValidationReport report() {
    return report;
  }

  private static Violation first(ValidationReport report) {
    List<Violation> errors = report.errors();
    if (errors.isEmpty()) {
      throw new IllegalArgumentException("report has no errors");
    }
    return errors.get(0);
  }

  private static Map<String, Object> details(ValidationReport report) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("violations", report.errors().stream().map(Violation::toMap).toList());
    details.put("warnings", report.warnings().stream().map(Violation::toMap).toList());
    return details;
  }
}
