package com.relay.domain.validation.rules;

import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.ValidationContext;
import com.relay.domain.validation.ValidationRule;
import com.relay.domain.validation.Violation;

/** A workflow must have at least one node ({@code empty_nodes}). Halts the chain when empty. */
public final class EmptyNodesRule extends ValidationRule {

  @Override
  protected boolean check(ValidationContext ctx) {
    if (ctx.definition().path("nodes").isEmpty()) {
      ctx.add(
          Violation.error(ErrorCode.EMPTY_NODES, "Workflow has no nodes", null, "nodes"));
      return false;
    }
    return true;
  }
}
