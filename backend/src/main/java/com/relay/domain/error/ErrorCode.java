package com.relay.domain.error;

import java.util.Locale;

/**
 * Every machine-readable error code Relay emits, with the HTTP status it maps to when surfaced by
 * the API. Engine-only codes (recorded on failed steps / runs) carry status 500 but are never
 * returned as HTTP errors. The wire form is the lowercase name, e.g. {@code invalid_node_type}.
 *
 * <p>HTTP mapping follows API_CONTRACT §7 "Suggested mapping"; see DESIGN/08-error-taxonomy.md.
 */
public enum ErrorCode {
  // --- auth (401 / 403)
  UNAUTHORIZED(401, Kind.API),
  FORBIDDEN(403, Kind.API),
  MISSING_WEBHOOK_SECRET(401, Kind.API),
  INVALID_WEBHOOK_SECRET(403, Kind.API),

  // --- not found (404)
  NOT_FOUND(404, Kind.API),
  WORKFLOW_NOT_FOUND(404, Kind.API),
  RUN_NOT_FOUND(404, Kind.API),
  APPROVAL_NOT_FOUND(404, Kind.API),

  // --- request shape (400)
  INVALID_REQUEST(400, Kind.API),
  INVALID_DEFINITION(400, Kind.API),
  METHOD_NOT_ALLOWED(405, Kind.API),

  // --- conflicts (409)
  WORKFLOW_EXISTS(409, Kind.API),
  WORKFLOW_NOT_PUBLISHED(409, Kind.API),
  TRIGGER_TYPE_MISMATCH(409, Kind.API),
  APPROVAL_ALREADY_DECIDED(409, Kind.API),
  RUN_ALREADY_TERMINAL(409, Kind.API),

  // --- publish validation (422) — API_CONTRACT §1 "distinct message per case"
  INVALID_NODE_TYPE(422, Kind.VALIDATION),
  MISSING_REQUIRED_PARAM(422, Kind.VALIDATION),
  INVALID_PARAM_VALUE(422, Kind.VALIDATION),
  INVALID_EDGE_TARGET(422, Kind.VALIDATION),
  INVALID_EDGE(422, Kind.VALIDATION),
  INVALID_ENTRY(422, Kind.VALIDATION),
  EMPTY_NODES(422, Kind.VALIDATION),
  DUPLICATE_NODE_IDS(422, Kind.VALIDATION),
  CYCLIC_ENTRY(422, Kind.VALIDATION),
  INVALID_TRIGGER(422, Kind.VALIDATION),
  INVALID_LIMITS(422, Kind.VALIDATION),
  INVALID_TEMPLATE(422, Kind.VALIDATION),
  // warnings (never block a publish)
  UNKNOWN_PARAM(422, Kind.VALIDATION),
  UNREACHABLE_NODE(422, Kind.VALIDATION),
  APPROVAL_NOT_GUARANTEED(422, Kind.VALIDATION),

  // --- engine (recorded on steps / runs)
  TEMPLATE_UNRESOLVABLE(500, Kind.ENGINE),
  APPROVAL_REQUIRED(500, Kind.ENGINE),
  APPROVAL_REJECTED(500, Kind.ENGINE),
  MAX_STEPS_EXCEEDED(500, Kind.ENGINE),
  TIMEOUT_EXCEEDED(500, Kind.ENGINE),
  TOKEN_BUDGET_EXCEEDED(500, Kind.ENGINE),
  CONDITION_NOT_NUMERIC(500, Kind.ENGINE),
  NODE_TIMEOUT(500, Kind.ENGINE),
  UPSTREAM_UNAVAILABLE(500, Kind.ENGINE),
  UPSTREAM_REJECTED(500, Kind.ENGINE),
  RETRIES_EXHAUSTED(500, Kind.ENGINE),
  AI_SCHEMA_VIOLATION(500, Kind.ENGINE),
  AI_PROVIDER_ERROR(500, Kind.ENGINE),
  NO_EXECUTOR(500, Kind.ENGINE),
  SNAPSHOT_SIGNATURE_INVALID(500, Kind.ENGINE),
  CANCELLED(500, Kind.ENGINE),

  // --- fallback
  INTERNAL_ERROR(500, Kind.API);

  /** Where a code is surfaced. */
  public enum Kind {
    /** Returned by the HTTP API. */
    API,
    /** Publish-validation violation (422 when blocking). */
    VALIDATION,
    /** Recorded on a step or run by the engine. */
    ENGINE
  }

  private final int httpStatus;
  private final Kind kind;

  ErrorCode(int httpStatus, Kind kind) {
    this.httpStatus = httpStatus;
    this.kind = kind;
  }

  /** HTTP status for API responses carrying this code. */
  public int httpStatus() {
    return httpStatus;
  }

  /** Where this code is surfaced. */
  public Kind kind() {
    return kind;
  }

  /** Wire form, e.g. {@code invalid_node_type}. */
  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }
}
