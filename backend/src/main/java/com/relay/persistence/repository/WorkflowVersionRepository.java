package com.relay.persistence.repository;

import com.relay.persistence.entity.WorkflowVersionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the immutable {@code workflow_versions}. */
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersionEntity, Long> {

  /** A specific version. */
  Optional<WorkflowVersionEntity> findByWorkflowIdAndVersion(String workflowId, int version);

  /** Version history, newest first. */
  List<WorkflowVersionEntity> findAllByWorkflowIdOrderByVersionDesc(String workflowId);
}
