package com.relay.application.compiler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Port: plain English → a candidate definition in the seed shape, or a refusal. Implementations
 * only <em>propose</em>; {@link CompilerService} is the referee (catalog-only types, publish
 * validation, approval-gate insertion).
 */
public interface WorkflowCompiler {

  /** Strategy name reported in results ({@code heuristic}, {@code llm}). */
  String name();

  /**
   * Proposes a workflow.
   *
   * @param description the user's description (the only input — never eval assertions)
   * @return a proposal
   */
  Proposal compile(String description);

  /**
   * A compiler's proposal: exactly one of {@code definition} / {@code refusal} is set.
   *
   * @param definition candidate definition (seed shape)
   * @param refusal refusal, when the catalog cannot express the request
   * @param explanation human explanation of the design
   * @param notes assumptions the builder should review
   */
  record Proposal(ObjectNode definition, Refusal refusal, String explanation, List<String> notes) {

    /** Canonical constructor; copies notes. */
    public Proposal {
      notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** A refusal proposal. */
    public static Proposal refuse(String reason, List<String> missing) {
      return new Proposal(null, new Refusal(reason, missing), null, List.of());
    }
  }

  /**
   * A refusal (API_CONTRACT §6 shape).
   *
   * @param reason why the catalog cannot express the request
   * @param missingCapabilities the missing capabilities
   */
  record Refusal(String reason, List<String> missingCapabilities) {
    /** Canonical constructor; copies the list. */
    public Refusal {
      missingCapabilities = missingCapabilities == null ? List.of() : List.copyOf(missingCapabilities);
    }
  }
}
