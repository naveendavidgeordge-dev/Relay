package com.relay.nodes;

/**
 * AI token usage for one step (summed over the repair attempt, if any).
 *
 * @param prompt prompt tokens
 * @param completion completion tokens
 */
public record TokenUsage(int prompt, int completion) {

  /** Total tokens. */
  public int total() {
    return prompt + completion;
  }

  /** Sum of two usages. */
  public TokenUsage plus(TokenUsage other) {
    return other == null ? this : new TokenUsage(prompt + other.prompt, completion + other.completion);
  }
}
