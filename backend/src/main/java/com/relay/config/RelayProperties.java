package com.relay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Typed view of the {@code relay.*} configuration tree (see {@code application.yml}).
 *
 * @param auth platform authentication
 * @param seed fixture locations and whether to seed workflows at startup
 * @param worker worker loop sizing and lease timing
 * @param engine engine feature flags
 * @param validation publish-validation severities
 * @param retry transient-failure retry policy for node execution
 * @param http outbound HTTP timeouts and URL aliases
 * @param mockWorld mock world base URL
 * @param ai AI sidecar connection
 * @param crypto key material and provider selection
 */
@ConfigurationProperties(prefix = "relay")
public record RelayProperties(
    Auth auth,
    Seed seed,
    Worker worker,
    Engine engine,
    Validation validation,
    Retry retry,
    Http http,
    MockWorld mockWorld,
    Ai ai,
    Crypto crypto) {

  /** Severity used by optional validation rules. */
  public enum Severity {
    WARN,
    REJECT
  }

  /**
   * Platform authentication settings.
   *
   * @param demoToken bearer token that grants every role (Must-Have demo auth)
   * @param jwtPublicKey optional PEM RSA public key enabling RS256 JWTs
   * @param jwtIssuer expected {@code iss} claim
   */
  public record Auth(String demoToken, String jwtPublicKey, String jwtIssuer) {}

  /**
   * Fixture locations.
   *
   * @param enabled whether seed workflows are loaded as published at startup
   * @param catalog node catalog resource
   * @param workflows seed workflows resource
   * @param samples sample payloads resource (JSONL)
   */
  public record Seed(boolean enabled, Resource catalog, Resource workflows, Resource samples) {}

  /**
   * Worker loop settings.
   *
   * @param enabled whether this process runs the worker loop
   * @param id optional stable worker identity (e.g. {@code worker-1}); when set, a restarted
   *     worker immediately releases leases held by its previous incarnation instead of waiting
   *     for them to expire
   * @param concurrency number of runs advanced in parallel
   * @param leaseSeconds queue-job lease length
   * @param pollIntervalMs idle poll interval
   */
  public record Worker(
      boolean enabled, String id, int concurrency, int leaseSeconds, long pollIntervalMs) {}

  /**
   * Engine flags.
   *
   * @param enforceTimeout enforce {@code limits.timeout_seconds} (Good To Have; see DESIGN/00 C8)
   */
  public record Engine(boolean enforceTimeout) {}

  /**
   * Publish-validation knobs.
   *
   * @param cyclicEntry severity of the {@code cyclic_entry} rule (DESIGN/00 C3)
   */
  public record Validation(Severity cyclicEntry) {}

  /**
   * Retry policy for transient node failures (exponential backoff with jitter).
   *
   * @param maxAttempts total attempts including the first
   * @param initialIntervalMs first backoff interval
   * @param multiplier backoff multiplier
   * @param maxIntervalMs backoff ceiling
   * @param randomizationFactor jitter factor in [0, 1)
   */
  public record Retry(
      int maxAttempts,
      long initialIntervalMs,
      double multiplier,
      long maxIntervalMs,
      double randomizationFactor) {}

  /**
   * Outbound HTTP settings.
   *
   * @param connectTimeoutMs connect timeout
   * @param readTimeoutMs response timeout
   * @param urlAliases comma-separated {@code from=>to} URL prefix rewrites (DESIGN/00 C2)
   * @param allowedHosts optional comma-separated host allowlist for {@code http_request} (empty =
   *     any host); guards against SSRF through templated URLs
   */
  public record Http(
      int connectTimeoutMs, int readTimeoutMs, String urlAliases, String allowedHosts) {}

  /**
   * Mock world.
   *
   * @param url base URL used by {@code notify} and {@code order_action}
   */
  public record MockWorld(String url) {}

  /**
   * AI sidecar.
   *
   * @param sidecarUrl base URL
   * @param timeoutMs per-call timeout
   * @param logPrompts log full prompts (off by default; privacy)
   */
  public record Ai(String sidecarUrl, int timeoutMs, boolean logPrompts) {}

  /**
   * Cryptography.
   *
   * @param provider {@code bouncycastle} or {@code test}
   * @param masterKey base64 32-byte KEK wrapping private keys
   * @param dbColumnKey pgcrypto symmetric key (defense in depth)
   */
  public record Crypto(String provider, String masterKey, String dbColumnKey) {}
}
