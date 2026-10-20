package com.relay.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.nodes.NodeExecutionContext;
import java.time.Instant;

/** Small factory for {@link NodeExecutionContext} in executor tests. */
public final class Contexts {

  private Contexts() {}

  /** A context for {@code nodeId} of {@code type} with resolved {@code params}. */
  public static NodeExecutionContext ctx(String type, String nodeId, JsonNode params, String key) {
    return NodeExecutionContext.builder()
        .runId("run_test")
        .workflowId("wf_test")
        .nodeId(nodeId)
        .nodeType(type)
        .sequence(1)
        .attempt(1)
        .params(params)
        .idempotencyKey(key)
        .stepStartedAt(Instant.parse("2026-07-04T10:00:00Z"))
        .traceId("0123456789abcdef0123456789abcdef")
        .build();
  }
}
