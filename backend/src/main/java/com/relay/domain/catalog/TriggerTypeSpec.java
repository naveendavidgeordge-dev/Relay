package com.relay.domain.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A trigger type from the catalog.
 *
 * @param type {@code webhook}, {@code schedule} or {@code manual}
 * @param description catalog description
 * @param config config keys (e.g. {@code secret}, {@code cron})
 */
public record TriggerTypeSpec(String type, String description, Map<String, ParamSpec> config) {

  /** Canonical constructor; defensively copies the config map. */
  public TriggerTypeSpec {
    config =
        config == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(config));
  }
}
