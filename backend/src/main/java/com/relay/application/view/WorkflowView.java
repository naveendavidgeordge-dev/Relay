package com.relay.application.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.List;

/**
 * Workflow as returned by the API. List responses omit the heavy fields ({@code definition},
 * {@code published_definition}, {@code versions}, {@code validation}); detail responses include
 * them. Webhook secrets are always rendered as {@code ********}.
 *
 * <p>Fixed contract: "{@code GET /workflows} — lists workflows with at least {@code id} and {@code
 * status}."
 *
 * @param id workflow id
 * @param name display name
 * @param description description
 * @param status {@code draft} or {@code published}
 * @param triggerType trigger type of the working copy
 * @param publishedVersion latest frozen version, or null
 * @param hasUnpublishedChanges working copy differs from the latest version
 * @param createdAt creation time
 * @param updatedAt last update time
 * @param runCount number of runs
 * @param lastRunAt last run trigger time
 * @param lastRunStatus last run status
 * @param webhookPath {@code /hooks/{id}} for webhook workflows
 * @param definition redacted working copy (detail only)
 * @param publishedDefinition redacted latest published definition (detail only)
 * @param versions version history (detail only)
 * @param validation current validation report of the working copy (detail only)
 */
public record WorkflowView(
    String id,
    String name,
    String description,
    WorkflowStatus status,
    String triggerType,
    Integer publishedVersion,
    boolean hasUnpublishedChanges,
    Instant createdAt,
    Instant updatedAt,
    long runCount,
    Instant lastRunAt,
    String lastRunStatus,
    String webhookPath,
    JsonNode definition,
    JsonNode publishedDefinition,
    List<VersionView> versions,
    ValidationView validation) {

  /**
   * One published version.
   *
   * @param version version number
   * @param publishedBy actor
   * @param createdAt publish time
   * @param signatureAlgorithm signature algorithm
   * @param signingKeyId key id
   * @param signatureValid whether the stored signature verifies
   */
  public record VersionView(
      int version,
      String publishedBy,
      Instant createdAt,
      String signatureAlgorithm,
      String signingKeyId,
      Boolean signatureValid) {}
}
