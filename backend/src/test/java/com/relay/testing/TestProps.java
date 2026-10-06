package com.relay.testing;

import com.relay.config.RelayProperties;
import org.springframework.core.io.ClassPathResource;

/** Builds {@link RelayProperties} for plain unit tests (no Spring context). */
public final class TestProps {

  private TestProps() {}

  /** Defaults suitable for unit tests: fast retries, short timeouts. */
  public static RelayProperties relay(
      String mockWorldUrl, String urlAliases, String allowedHosts, int readTimeoutMs) {
    return new RelayProperties(
        new RelayProperties.Auth("relay-demo-token", "", "relay"),
        new RelayProperties.Seed(
            true,
            new ClassPathResource("catalog/node_catalog.json"),
            new ClassPathResource("seed/seed_workflows.json"),
            new ClassPathResource("seed/sample_payloads.jsonl")),
        new RelayProperties.Worker(true, null, 2, 15, 50),
        new RelayProperties.Engine(false),
        new RelayProperties.Validation(RelayProperties.Severity.WARN),
        new RelayProperties.Retry(3, 10, 2.0, 50, 0.5),
        new RelayProperties.Http(500, readTimeoutMs, urlAliases, allowedHosts),
        new RelayProperties.MockWorld(mockWorldUrl),
        new RelayProperties.Ai("http://localhost:8090", 2000, false),
        new RelayProperties.Crypto("test", "ZGV2LW9ubHktbWFzdGVyLWtleS1jaGFuZ2UtbWUtMzI=", "k"));
  }

  /** Defaults pointing the mock world at {@code url}. */
  public static RelayProperties relay(String mockWorldUrl) {
    return relay(mockWorldUrl, "", "", 1000);
  }
}
