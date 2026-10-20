package com.relay.domain.validation;

/**
 * A link in the publish-validation Chain of Responsibility.
 *
 * <p>Each rule inspects the context, records violations, and decides whether the chain continues.
 * A rule returns {@code false} only when the definition is too broken for later rules to say
 * anything meaningful (e.g. {@code nodes} is not an array), so a caller normally gets every
 * independent problem in one response.
 */
public abstract class ValidationRule {

  private ValidationRule next;

  /**
   * Appends {@code next} after this rule.
   *
   * @param next following rule
   * @return {@code next}, for fluent chaining
   */
  public final ValidationRule linkWith(ValidationRule next) {
    this.next = next;
    return next;
  }

  /**
   * Runs this rule and, unless it halts, the rest of the chain.
   *
   * @param ctx validation context
   */
  public final void validate(ValidationContext ctx) {
    boolean proceed = check(ctx);
    if (proceed && next != null) {
      next.validate(ctx);
    }
  }

  /**
   * Performs this rule's check.
   *
   * @param ctx validation context
   * @return {@code true} to continue the chain, {@code false} to stop
   */
  protected abstract boolean check(ValidationContext ctx);
}
