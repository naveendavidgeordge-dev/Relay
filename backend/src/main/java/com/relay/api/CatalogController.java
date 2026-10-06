package com.relay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.application.SamplePayloads;
import com.relay.domain.catalog.NodeCatalog;
import com.relay.domain.error.NotFoundException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only reference data: the node catalog and the sample trigger payloads. Not part of the
 * fixed contract; documented in docs/api.md. The console uses them to render node badges and to
 * offer "trigger with sample payload".
 */
@RestController
public class CatalogController {

  private final NodeCatalog catalog;
  private final SamplePayloads samples;

  /**
   * Creates the controller.
   *
   * @param catalog node catalog
   * @param samples sample payloads
   */
  public CatalogController(NodeCatalog catalog, SamplePayloads samples) {
    this.catalog = catalog;
    this.samples = samples;
  }

  /** The node catalog exactly as loaded from {@code data/node_catalog.json}. */
  @GetMapping("/catalog")
  public JsonNode catalog() {
    return catalog.raw();
  }

  /**
   * Sample payloads, optionally filtered by target workflow.
   *
   * @param workflow optional workflow id filter
   * @return samples
   */
  @GetMapping("/samples")
  public List<JsonNode> samples(@RequestParam(required = false) String workflow) {
    return samples.all().stream()
        .filter(s -> workflow == null || workflow.equals(s.path("workflow").asText()))
        .toList();
  }

  /**
   * One sample payload.
   *
   * @param id sample id, e.g. {@code pay_inject_001}
   * @return the sample
   */
  @GetMapping("/samples/{id}")
  public JsonNode sample(@PathVariable String id) {
    return samples
        .byId(id)
        .orElseThrow(
            () ->
                new NotFoundException(
                    com.relay.domain.error.ErrorCode.NOT_FOUND, "Sample '" + id + "' not found"));
  }
}
