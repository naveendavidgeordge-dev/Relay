package com.relay.domain.template;

import java.util.List;

/**
 * A parsed template reference.
 *
 * @param root what the path starts from
 * @param nodeId referenced node for {@link Root#NODE_OUTPUT}, else null
 * @param path segments after {@code trigger.body} / {@code nodes.<id>.output}
 * @param expression the original expression, for error messages
 */
public record TemplateRef(Root root, String nodeId, List<String> path, String expression) {

  /** Canonical constructor; copies the path. */
  public TemplateRef {
    path = List.copyOf(path);
  }

  /** Path roots allowed by the grammar. */
  public enum Root {
    /** {@code trigger.body…} — the (untrusted) trigger payload. */
    TRIGGER_BODY,
    /** {@code nodes.<id>.output…} — a previously executed node's persisted output. */
    NODE_OUTPUT
  }
}
