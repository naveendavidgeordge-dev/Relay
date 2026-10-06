package com.relay.support;

import org.slf4j.MDC;

/**
 * Trace-id propagation through the MDC. The id enters at the HTTP edge ({@code X-Trace-Id} /
 * {@code traceparent}), is stored on the run and its queue job, and is restored by the worker so
 * logs from the API request and from every step of the run share one id.
 */
public final class TraceContext {

  /** MDC key for the trace id. */
  public static final String TRACE_ID = "trace_id";

  /** MDC key for the run id. */
  public static final String RUN_ID = "run_id";

  /** MDC key for the node id. */
  public static final String NODE_ID = "node_id";

  /** MDC key for the worker id. */
  public static final String WORKER_ID = "worker_id";

  private TraceContext() {}

  /** Returns the current trace id, or {@code null} outside a traced context. */
  public static String currentTraceId() {
    return MDC.get(TRACE_ID);
  }

  /** Returns the current trace id, creating and binding a new one if absent. */
  public static String currentOrNew() {
    String id = MDC.get(TRACE_ID);
    if (id == null || id.isBlank()) {
      id = Ids.traceId();
      MDC.put(TRACE_ID, id);
    }
    return id;
  }

  /**
   * Binds run-scoped keys for the duration of a worker job.
   *
   * @param traceId trace id stored on the job (may be null)
   * @param runId run being advanced
   * @return a scope that restores the previous MDC on close
   */
  public static Scope bindRun(String traceId, String runId) {
    var previous = MDC.getCopyOfContextMap();
    MDC.put(TRACE_ID, traceId != null ? traceId : Ids.traceId());
    MDC.put(RUN_ID, runId);
    return () -> {
      if (previous == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(previous);
      }
    };
  }

  /** An MDC scope that does not throw on close. */
  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
