package com.relay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.config.RelayProperties;
import com.relay.domain.error.ConflictException;
import com.relay.persistence.repository.WorkflowRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Loads {@code data/seed_workflows.json} at startup and publishes each workflow.
 *
 * <p>"Load the node catalog and the four seed workflows as Published." — IMPLEMENTATION_GUIDE,
 * Must Have Checklist. Seeding is idempotent: existing workflows (including ones a user edited)
 * are left untouched, so restarts never clobber changes or create duplicate versions.
 */
@Component
@Order(10)
@ConditionalOnProperty(prefix = "relay.seed", name = "enabled", havingValue = "true")
public class SeedLoader implements ApplicationRunner {

  /** Actor recorded on seeded versions and audit entries. */
  public static final String SEED_ACTOR = "system:seed";

  private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

  private final RelayProperties props;
  private final ObjectMapper mapper;
  private final WorkflowService workflowService;
  private final WorkflowRepository workflows;

  /**
   * Creates the loader.
   *
   * @param props relay properties
   * @param mapper Jackson mapper
   * @param workflowService workflow service
   * @param workflows workflow repository
   */
  public SeedLoader(
      RelayProperties props,
      ObjectMapper mapper,
      WorkflowService workflowService,
      WorkflowRepository workflows) {
    this.props = props;
    this.mapper = mapper;
    this.workflowService = workflowService;
    this.workflows = workflows;
  }

  @Override
  public void run(ApplicationArguments args) {
    JsonNode root;
    try (InputStream in = props.seed().workflows().getInputStream()) {
      root = mapper.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read seed workflows", e);
    }
    int created = 0;
    for (JsonNode def : root.path("workflows")) {
      String id = def.path("id").asText();
      if (workflows.existsById(id)) {
        log.debug("Seed {} already present; leaving it untouched", id);
        continue;
      }
      try {
        workflowService.create(def, SEED_ACTOR);
        workflowService.publish(id, SEED_ACTOR);
        created++;
      } catch (ConflictException e) {
        // Another API instance seeded it concurrently.
        log.info("Seed {} was created concurrently: {}", id, e.getMessage());
      }
    }
    log.info("Seed workflows loaded: {} created, {} total", created, root.path("workflows").size());
  }
}
