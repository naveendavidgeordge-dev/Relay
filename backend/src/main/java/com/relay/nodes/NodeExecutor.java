package com.relay.nodes;

/**
 * Strategy for executing one catalog node type. Exactly one Spring bean per type; {@code
 * engine.NodeExecutorRegistry} refuses to start if a catalog type has no executor or an executor
 * names a type the catalog does not define ("Never emit a node type outside node_catalog.json").
 *
 * <p>Contract for implementations:
 *
 * <ul>
 *   <li>Params arrive already template-resolved and catalog-validated.
 *   <li>Side-effect executors must send {@link NodeExecutionContext#idempotencyKey()} on every
 *       mutating call, unchanged across retries and resumes.
 *   <li>Throw {@link TransientNodeException} for retryable failures (5xx, 429, timeouts,
 *       connection errors) and {@link NodeFailureException} for permanent ones. Never swallow.
 *   <li>Every external call has a timeout.
 * </ul>
 */
public interface NodeExecutor {

  /** The catalog type this executor handles, e.g. {@code notify}. */
  String type();

  /**
   * Executes the node.
   *
   * @param ctx execution context
   * @return the result
   * @throws TransientNodeException on retryable failure
   * @throws NodeFailureException on permanent failure
   */
  NodeResult execute(NodeExecutionContext ctx);
}
