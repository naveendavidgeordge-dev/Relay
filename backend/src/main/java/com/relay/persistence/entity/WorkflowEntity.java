package com.relay.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Row of {@code workflows}: the editable working copy plus a pointer to the latest version. */
@Entity
@Table(name = "workflows")
public class WorkflowEntity {

  @Id
  @Column(name = "id", nullable = false, length = 128)
  private String id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  @Column(name = "trigger_type", nullable = false, length = 32)
  private String triggerType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
  private String definitionJson;

  @Column(name = "published_version")
  private Integer publishedVersion;

  @Column(name = "has_unpublished_changes", nullable = false)
  private boolean hasUnpublishedChanges;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  @PreUpdate
  void touch() {
    this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    if (this.createdAt == null) {
      this.createdAt = this.updatedAt;
    }
  }

  protected WorkflowEntity() {}

  /**
   * Creates a new draft row.
   *
   * @param id workflow id
   * @param name display name
   * @param description optional description
   * @param triggerType trigger type
   * @param definitionJson secret-free working copy
   * @param now creation time
   */
  public WorkflowEntity(
      String id,
      String name,
      String description,
      String triggerType,
      String definitionJson,
      Instant now) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.status = "draft";
    this.triggerType = triggerType;
    this.definitionJson = definitionJson;
    this.hasUnpublishedChanges = true;
    this.createdAt = now;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getStatus() {
    return status;
  }

  public String getTriggerType() {
    return triggerType;
  }

  public String getDefinitionJson() {
    return definitionJson;
  }

  public Integer getPublishedVersion() {
    return publishedVersion;
  }

  public boolean isHasUnpublishedChanges() {
    return hasUnpublishedChanges;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Replaces the working copy (never touches published versions).
   *
   * @param name new name
   * @param description new description
   * @param triggerType new trigger type
   * @param definitionJson new secret-free definition
   */
  public void updateWorkingCopy(
      String name, String description, String triggerType, String definitionJson) {
    this.name = name;
    this.description = description;
    this.triggerType = triggerType;
    this.definitionJson = definitionJson;
    this.hasUnpublishedChanges = true;
  }

  /**
   * Records that the working copy was frozen as {@code version}.
   *
   * @param version the new published version number
   */
  public void markPublished(int version) {
    this.status = "published";
    this.publishedVersion = version;
    this.hasUnpublishedChanges = false;
  }
}
