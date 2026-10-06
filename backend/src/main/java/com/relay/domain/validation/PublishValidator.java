package com.relay.domain.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.domain.catalog.NodeCatalog;
import com.relay.domain.validation.rules.ApprovalPrecedenceRule;
import com.relay.domain.validation.rules.DuplicateNodeIdsRule;
import com.relay.domain.validation.rules.EdgeRule;
import com.relay.domain.validation.rules.EmptyNodesRule;
import com.relay.domain.validation.rules.EntryRule;
import com.relay.domain.validation.rules.LimitsRule;
import com.relay.domain.validation.rules.NodeTypeRule;
import com.relay.domain.validation.rules.ParamsRule;
import com.relay.domain.validation.rules.ReachabilityRule;
import com.relay.domain.validation.rules.ShapeRule;
import com.relay.domain.validation.rules.TemplateRule;
import com.relay.domain.validation.rules.TriggerRule;

/**
 * Publish-time validation of a definition against the node catalog, built as a Chain of
 * Responsibility. Rule order determines which violation names the response's top-level {@code
 * code}; independent problems are all reported in {@code details.violations}.
 *
 * <p>"Publish-time validation rejects unknown node types, missing required parameters, and
 * references to nonexistent nodes — each with a clear error." — Case Study, Must Have §2
 *
 * <p>Pure domain object (no Spring); the NL compiler reuses it as its referee.
 */
public final class PublishValidator {

  private final NodeCatalog catalog;
  private final ValidationRule chain;

  /**
   * Builds the chain.
   *
   * @param catalog node catalog
   * @param rejectCyclicEntry whether {@code cyclic_entry} blocks publishing (default false)
   */
  public PublishValidator(NodeCatalog catalog, boolean rejectCyclicEntry) {
    this.catalog = catalog;
    ValidationRule head = new ShapeRule();
    head.linkWith(new EmptyNodesRule())
        .linkWith(new DuplicateNodeIdsRule())
        .linkWith(new TriggerRule())
        .linkWith(new NodeTypeRule())
        .linkWith(new ParamsRule())
        .linkWith(new EdgeRule())
        .linkWith(new EntryRule())
        .linkWith(new LimitsRule())
        .linkWith(new TemplateRule())
        .linkWith(new ReachabilityRule(rejectCyclicEntry))
        .linkWith(new ApprovalPrecedenceRule());
    this.chain = head;
  }

  /**
   * Runs the full chain.
   *
   * @param definition raw definition JSON
   * @return the report (never throws for bad input)
   */
  public ValidationReport validate(JsonNode definition) {
    ValidationContext ctx = new ValidationContext(definition, catalog);
    chain.validate(ctx);
    return ctx.report();
  }

  /**
   * Runs only the structural shape rule (used at create/update time).
   *
   * @param definition raw definition JSON
   * @return the report
   */
  public ValidationReport validateShape(JsonNode definition) {
    ValidationContext ctx = new ValidationContext(definition, catalog);
    new ShapeRule().validate(ctx);
    return ctx.report();
  }
}
