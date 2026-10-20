package com.relay.domain.validation;

import java.util.List;

/**
 * Result of running the validation chain.
 *
 * @param violations all findings in rule order
 */
public record ValidationReport(List<Violation> violations) {

  /** Canonical constructor; copies the list. */
  public ValidationReport {
    violations = List.copyOf(violations);
  }

  /** Blocking violations in rule order (the first one names the response's top-level code). */
  public List<Violation> errors() {
    return violations.stream().filter(Violation::isError).toList();
  }

  /** Non-blocking warnings. */
  public List<Violation> warnings() {
    return violations.stream().filter(v -> !v.isError()).toList();
  }

  /** True when nothing blocks publishing. */
  public boolean isValid() {
    return violations.stream().noneMatch(Violation::isError);
  }
}
