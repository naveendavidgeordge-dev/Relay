package com.relay.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.domain.catalog.NodeCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the canonical node catalog at startup. The classpath copy is produced from {@code
 * data/node_catalog.json} by the Gradle {@code processResources} task, never edited by hand.
 *
 * <p>"Load the provided node catalog, seed workflows, and sample trigger payloads" — Case Study,
 * Must Have §1
 */
@Configuration(proxyBeanMethods = false)
public class CatalogConfig {

  private static final Logger log = LoggerFactory.getLogger(CatalogConfig.class);

  /**
   * The node catalog, shared by validation, the engine and the compiler.
   *
   * @param props relay properties
   * @param mapper Jackson mapper
   * @return the parsed catalog
   */
  @Bean
  public NodeCatalog nodeCatalog(RelayProperties props, ObjectMapper mapper) {
    try (InputStream in = props.seed().catalog().getInputStream()) {
      JsonNode root = mapper.readTree(in);
      NodeCatalog catalog = NodeCatalog.parse(root);
      log.info(
          "Loaded node catalog: nodes={} triggers={}",
          catalog.nodeTypes(),
          catalog.triggerTypes());
      return catalog;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read node catalog " + props.seed().catalog(), e);
    }
  }

  /** UTC system clock; injected everywhere time matters so tests can control it. */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
