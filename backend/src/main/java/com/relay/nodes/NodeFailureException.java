package com.relay.nodes;

import com.relay.domain.error.ErrorCode;
import java.util.Map;

/**
 * A permanent failure: the step fails without further retries and the run ends {@code failed}
 * with the error recorded (business-rule rejections, schema violations after repair, unresolvable
 * templates, non-numeric comparisons, missing approval).
 */
public class NodeFailureException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;
  private final transient Map<String, Object> details;

  /**
   * Creates the exception.
   *
   * @param code error code
   * @param message caller-safe message
   * @param details structured details (no secrets)
   */
  public NodeFailureException(ErrorCode code, String message, Map<String, Object> details) {
    super(message);
    this.code = code;
    this.details = details == null ? Map.of() : Map.copyOf(details);
  }

  /**
   * Creates the exception without details.
   *
   * @param code error code
   * @param message caller-safe message
   */
  public NodeFailureException(ErrorCode code, String message) {
    this(code, message, Map.of());
  }

  /** The error code. */
  public ErrorCode code() {
    return code;
  }

  /** Structured details. */
  public Map<String, Object> details() {
    return details;
  }
}
