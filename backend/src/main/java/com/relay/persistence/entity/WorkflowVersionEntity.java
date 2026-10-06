package com.relay.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Row of {@code workflow_versions}: an immutable, signed published definition. */
@Entity
@Immutable
@Table(name = "workflow_versions")
public class WorkflowVersionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "workflow_id", nullable = false, length = 128)
  private String workflowId;

  @Column(name = "version", nullable = false)
  private int version;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
  private String definitionJson;

  @Column(name = "signature")
  private byte[] signature;

  @Column(name = "signing_key_id", length = 64)
  private String signingKeyId;

  @Column(name = "published_by", nullable = false, length = 128)
  private String publishedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected WorkflowVersionEntity() {}

  /**
   * Creates a version row.
   *
   * @param workflowId owning workflow
   * @param version version number (1-based)
   * @param definitionJson frozen, secret-free definition
   * @param signature ML-DSA-65 signature over the canonical definition
   * @param signingKeyId id of the signing key
   * @param publishedBy actor
   * @param createdAt publish time
   */
  public WorkflowVersionEntity(
      String workflowId,
      int version,
      String definitionJson,
      byte[] signature,
      String signingKeyId,
      String publishedBy,
      Instant createdAt) {
    this.workflowId = workflowId;
    this.version = version;
    this.definitionJson = definitionJson;
    this.signature = signature == null ? null : signature.clone();
    this.signingKeyId = signingKeyId;
    this.publishedBy = publishedBy;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public int getVersion() {
    return version;
  }

  public String getDefinitionJson() {
    return definitionJson;
  }

  public byte[] getSignature() {
    return signature == null ? null : signature.clone();
  }

  public String getSigningKeyId() {
    return signingKeyId;
  }

  public String getPublishedBy() {
    return publishedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
