package com.relay.domain.error;

import java.util.Map;

/** A referenced workflow, run or approval does not exist (404). */
public final class NotFoundException extends RelayException {

  /**
   * Creates the exception.
   *
   * @param code one of the {@code *_NOT_FOUND} codes
   * @param message caller-safe message
   */
  public NotFoundException(ErrorCode code, String message) {
    super(code, message, Map.of());
  }

  /** Unknown workflow id. */
  public static NotFoundException workflow(String id) {
    return new NotFoundException(ErrorCode.WORKFLOW_NOT_FOUND, "Workflow '" + id + "' not found");
  }

  /** Unknown run id. */
  public static NotFoundException run(String id) {
    return new NotFoundException(ErrorCode.RUN_NOT_FOUND, "Run '" + id + "' not found");
  }

  /** Unknown approval id. */
  public static NotFoundException approval(String id) {
    return new NotFoundException(ErrorCode.APPROVAL_NOT_FOUND, "Approval '" + id + "' not found");
  }
}
