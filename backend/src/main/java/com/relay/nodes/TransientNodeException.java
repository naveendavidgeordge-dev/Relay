package com.relay.nodes;

import com.relay.domain.error.ErrorCode;

/**
 * A retryable failure: the engine retries with exponential backoff and jitter, reusing the same
 * idempotency key ("Per-node retries with exponential backoff for transient failures" — Case
 * Study, Must Have §7).
 */
public class TransientNodeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;

  /**
   * Creates the exception.
   *
   * @param code {@code upstream_unavailable}, {@code node_timeout}, …
   * @param message caller-safe message (no secrets)
   */
  public TransientNodeException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  /**
   * Creates the exception with a cause.
   *
   * @param code error code
   * @param message caller-safe message
   * @param cause cause
   */
  public TransientNodeException(ErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  /** The error code. */
  public ErrorCode code() {
    return code;
  }
}
