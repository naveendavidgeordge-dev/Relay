package com.relay.api;

import com.relay.support.Ids;
import com.relay.support.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the request's trace id before anything else runs (including security, so 401 bodies
 * carry it too). Accepts W3C {@code traceparent} or {@code X-Trace-Id}; otherwise mints one. The
 * id is echoed in {@code X-Trace-Id}, stored on runs and queue jobs, and restored by the worker.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  /** Response / request header. */
  public static final String HEADER = "X-Trace-Id";

  private static final Pattern HEX32 = Pattern.compile("^[0-9a-f]{32}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = fromTraceparent(request.getHeader("traceparent"));
    if (traceId == null) {
      String h = request.getHeader(HEADER);
      traceId = h != null && HEX32.matcher(h.toLowerCase()).matches() ? h.toLowerCase() : null;
    }
    if (traceId == null) {
      traceId = Ids.traceId();
    }
    MDC.put(TraceContext.TRACE_ID, traceId);
    response.setHeader(HEADER, traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(TraceContext.TRACE_ID);
    }
  }

  private static String fromTraceparent(String header) {
    // version-traceid-parentid-flags, e.g. 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
    if (header == null) {
      return null;
    }
    String[] parts = header.split("-");
    return parts.length == 4 && HEX32.matcher(parts[1]).matches() ? parts[1] : null;
  }
}
