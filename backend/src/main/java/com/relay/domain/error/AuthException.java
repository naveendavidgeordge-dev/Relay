package com.relay.domain.error;

import java.util.Map;

/** Authentication / webhook-secret failures (401 / 403). Never echoes the presented secret. */
public final class AuthException extends RelayException {

  /**
   * Creates the exception.
   *
   * @param code an auth code
   * @param message caller-safe message
   */
  public AuthException(ErrorCode code, String message) {
    super(code, message, Map.of());
  }
}
