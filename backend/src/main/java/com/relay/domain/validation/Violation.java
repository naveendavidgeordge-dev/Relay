package com.relay.domain.validation;

import com.relay.domain.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One finding from publish validation.
 *
 * @param code distinct error code per case (API_CONTRACT §1 "distinct message per case")
 * @param message human-readable, caller-safe message
 * @param severity blocking error or warning
 * @param nodeId offending node, if any
 * @param field offending field (e.g. {@code next}, {@code params.to}), if any
 */
public record Violation(
    ErrorCode code, String message, Severity severity, String nodeId, String field) {

  /** Whether a violation blocks publishing. */
  public enum Severity {
    ERROR,
    WARNING
  }

  /** Blocking violation. */
  public static Violation error(ErrorCode code, String message, String nodeId, String field) {
    return new Violation(code, message, Severity.ERROR, nodeId, field);
  }

  /** Non-blocking warning. */
  public static Violation warning(ErrorCode code, String message, String nodeId, String field) {
    return new Violation(code, message, Severity.WARNING, nodeId, field);
  }

  /** True for blocking violations. */
  public boolean isError() {
    return severity == Severity.ERROR;
  }

  /** Null-free map form for error {@code details}. */
  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", code.code());
    m.put("message", message);
    if (nodeId != null) {
      m.put("node_id", nodeId);
    }
    if (field != null) {
      m.put("field", field);
    }
    return m;
  }
}
