package com.relay.domain.catalog;

import java.util.List;

/**
 * One parameter of a node (or one config key of a trigger), copied verbatim from {@code
 * node_catalog.json}.
 *
 * @param name parameter name, e.g. {@code channel}
 * @param type catalog type: {@code string}, {@code number}, {@code object}
 * @param required whether publish validation demands it
 * @param enumValues allowed values, empty when unconstrained
 * @param templatable whether {@code {{...}}} templates are resolved in it
 * @param description human description (may be null)
 */
public record ParamSpec(
    String name,
    String type,
    boolean required,
    List<String> enumValues,
    boolean templatable,
    String description) {

  /** Canonical constructor; defensively copies the enum list. */
  public ParamSpec {
    enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
  }

  /** True if the catalog constrains this param to a fixed set of values. */
  public boolean hasEnum() {
    return !enumValues.isEmpty();
  }
}
