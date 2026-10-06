package com.relay.persistence.repository;

import com.relay.persistence.entity.WorkflowEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@code workflows}. */
public interface WorkflowRepository extends JpaRepository<WorkflowEntity, String> {

  /** All workflows, oldest first (seeds first, in file order). */
  List<WorkflowEntity> findAllByOrderByCreatedAtAscIdAsc();

  /** Workflows in a given status ({@code draft} / {@code published}), oldest first. */
  List<WorkflowEntity> findAllByStatusOrderByCreatedAtAscIdAsc(String status);
}
