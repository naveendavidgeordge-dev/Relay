package com.relay.api.error;

import com.relay.domain.error.ErrorCode;
import com.relay.domain.error.RelayException;
import com.relay.support.TraceContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception to the uniform error body. Never leaks stack traces, SQL, secrets or class
 * names: unexpected exceptions become {@code 500 internal_error} with only the trace id, and the
 * full exception goes to the (masked) log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * Relay's own exceptions carry their code and status.
   *
   * @param e exception
   * @return error response
   */
  @ExceptionHandler(RelayException.class)
  public ResponseEntity<ErrorResponse> relay(RelayException e) {
    return respond(e.code(), e.getMessage(), e.details());
  }

  /**
   * Unparseable JSON bodies.
   *
   * @param e exception
   * @return 400
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
    return respond(ErrorCode.INVALID_REQUEST, "Request body is missing or is not valid JSON", null);
  }

  /**
   * Bad query parameters.
   *
   * @param e exception
   * @return 400
   */
  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<ErrorResponse> badParam(Exception e) {
    String msg =
        e instanceof MethodArgumentTypeMismatchException m
            ? "Invalid value for parameter '" + m.getName() + "'"
            : e instanceof MissingServletRequestParameterException p
                ? "Missing parameter '" + p.getParameterName() + "'"
                : "Invalid request";
    return respond(ErrorCode.INVALID_REQUEST, msg, null);
  }

  /**
   * Unsupported content type.
   *
   * @param e exception
   * @return 400
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException e) {
    return respond(ErrorCode.INVALID_REQUEST, "Content-Type must be application/json", null);
  }

  /**
   * Unknown route.
   *
   * @param e exception
   * @return 404
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> noRoute(NoResourceFoundException e) {
    return respond(ErrorCode.NOT_FOUND, "No such route", null);
  }

  /**
   * Wrong method.
   *
   * @param e exception
   * @return 405
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException e) {
    return respond(ErrorCode.METHOD_NOT_ALLOWED, "Method " + e.getMethod() + " not allowed", null);
  }

  /**
   * Role check failed (method security).
   *
   * @param e exception
   * @return 403
   */
  @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
  public ResponseEntity<ErrorResponse> denied(RuntimeException e) {
    return respond(ErrorCode.FORBIDDEN, "Your token does not grant the role this action needs", null);
  }

  /**
   * Anything else.
   *
   * @param e exception
   * @return 500 with no internals
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> unexpected(Exception e) {
    log.error("Unhandled exception", e);
    return respond(
        ErrorCode.INTERNAL_ERROR,
        "Internal error; quote trace_id " + TraceContext.currentTraceId() + " when reporting",
        null);
  }

  private static ResponseEntity<ErrorResponse> respond(
      ErrorCode code, String message, Map<String, Object> details) {
    return ResponseEntity.status(code.httpStatus())
        .body(ErrorResponse.of(message, code.code(), TraceContext.currentTraceId(), details));
  }
}
