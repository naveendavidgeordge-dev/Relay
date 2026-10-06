package com.relay.domain.error;

import java.util.Map;

/** The request is valid but conflicts with current state (409). */
public final class ConflictException extends RelayException {

  /**
   * Creates the exception.
   *
   * @param code a 409 code
   * @param message caller-safe message
   * @param details optional structured details
   */
  public ConflictException(ErrorCode code, String message, Map<String, Object> details) {
    super(code, message, details);
  }

  /**
   * Creates the exception without details.
   *
   * @param code a 409 code
   * @param message caller-safe message
   */
  public ConflictException(ErrorCode code, String message) {
    this(code, message, Map.of());
  }
}
