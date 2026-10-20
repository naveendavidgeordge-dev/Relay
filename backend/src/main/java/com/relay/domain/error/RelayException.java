package com.relay.domain.error;

import java.util.Map;

/**
 * Root of every error the API surfaces to callers. The global exception handler renders it as the
 * uniform body {@code {"error": {"message", "code", "trace_id", "details"}}} with the status from
 * {@link ErrorCode#httpStatus()}.
 *
 * <p>Messages must be safe to show to a caller: never include secrets, tokens, stack traces or
 * internal hostnames.
 */
public abstract sealed class RelayException extends RuntimeException
    permits NotFoundException,
        ConflictException,
        AuthException,
        BadRequestException,
        DefinitionValidationException {

  private final ErrorCode code;
  private final transient Map<String, Object> details;

  protected RelayException(ErrorCode code, String message, Map<String, Object> details) {
    super(message);
    this.code = code;
    this.details = details == null ? Map.of() : Map.copyOf(details);
  }

  /** The machine-readable code. */
  public ErrorCode code() {
    return code;
  }

  /** Optional structured details (never secrets). */
  public Map<String, Object> details() {
    return details;
  }
}
