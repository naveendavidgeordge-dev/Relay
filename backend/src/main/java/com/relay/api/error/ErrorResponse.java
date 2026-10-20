package com.relay.api.error;

import java.util.Map;

/**
 * The uniform error envelope: {@code {"error": {"message", "code", "trace_id", "details"}}}.
 *
 * <p>"Every rejection returns a clear error body" — API_CONTRACT §7.
 *
 * @param error the error
 */
public record ErrorResponse(Body error) {

  /**
   * Builds an error response.
   *
   * @param message caller-safe message
   * @param code machine-readable code
   * @param traceId trace id of the request
   * @param details optional details
   * @return the response
   */
  public static ErrorResponse of(
      String message, String code, String traceId, Map<String, Object> details) {
    return new ErrorResponse(
        new Body(message, code, traceId, details == null || details.isEmpty() ? null : details));
  }

  /**
   * Error body.
   *
   * @param message caller-safe message
   * @param code machine-readable code
   * @param traceId request trace id
   * @param details optional structured details
   */
  public record Body(String message, String code, String traceId, Map<String, Object> details) {}
}
