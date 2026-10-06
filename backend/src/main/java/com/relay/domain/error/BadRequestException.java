package com.relay.domain.error;

import java.util.Map;

/** Malformed request or definition shape (400). */
public final class BadRequestException extends RelayException {

  /**
   * Creates the exception.
   *
   * @param code a 400 code
   * @param message caller-safe message
   * @param details optional structured details
   */
  public BadRequestException(ErrorCode code, String message, Map<String, Object> details) {
    super(code, message, details);
  }

  /**
   * Creates the exception without details.
   *
   * @param code a 400 code
   * @param message caller-safe message
   */
  public BadRequestException(ErrorCode code, String message) {
    this(code, message, Map.of());
  }
}
