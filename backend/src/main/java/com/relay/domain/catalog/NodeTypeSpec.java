package com.relay.domain.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A node type from the catalog.
 *
 * @param type type name, e.g. {@code order_action}
 * @param description catalog description
 * @param sideEffect {@code side_effect}: must send an Idempotency-Key
 * @param requiresApproval {@code requires_approval}: engine-gated on an approved approval row
 * @param params parameter specs in catalog order
 * @param branches branch edge names ({@code on_true}, {@code on_false}) or empty for {@code next}
 */
public record NodeTypeSpec(
    String type,
    String description,
    boolean sideEffect,
    boolean requiresApproval,
    Map<String, ParamSpec> params,
    List<String> branches) {

  /** Canonical constructor; defensively copies collections (params keep catalog order). */
  public NodeTypeSpec {
    params =
        params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    branches = branches == null ? List.of() : List.copyOf(branches);
  }

  /** True for node types routed by {@code on_true}/{@code on_false} instead of {@code next}. */
  public boolean isBranching() {
    return !branches.isEmpty();
  }
}
