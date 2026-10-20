package com.relay.engine;

import com.relay.domain.catalog.NodeCatalog;
import com.relay.nodes.NodeExecutor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Strategy registry: node type → {@link NodeExecutor}. Built from every executor bean and checked
 * against the catalog at startup in both directions — an executor for a type the catalog lacks
 * would let the engine run something publish validation can never produce, so it is fatal.
 */
@Component
public class NodeExecutorRegistry {

  private static final Logger log = LoggerFactory.getLogger(NodeExecutorRegistry.class);

  private final Map<String, NodeExecutor> byType;

  /**
   * Builds and validates the registry.
   *
   * @param executors all executor beans
   * @param catalog node catalog
   */
  public NodeExecutorRegistry(List<NodeExecutor> executors, NodeCatalog catalog) {
    Map<String, NodeExecutor> map = new HashMap<>();
    for (NodeExecutor e : executors) {
      if (catalog.node(e.type()).isEmpty()) {
        throw new IllegalStateException(
            "Executor " + e.getClass().getSimpleName() + " handles '" + e.type()
                + "', which is not in node_catalog.json");
      }
      NodeExecutor prev = map.put(e.type(), e);
      if (prev != null) {
        throw new IllegalStateException("Two executors for node type '" + e.type() + "'");
      }
    }
    Set<String> missing = new TreeSet<>(catalog.nodeTypes());
    missing.removeAll(map.keySet());
    if (!missing.isEmpty()) {
      throw new IllegalStateException("No executor registered for catalog node types " + missing);
    }
    this.byType = Map.copyOf(map);
    log.info("Node executors registered: {}", new TreeSet<>(byType.keySet()));
  }

  /** The executor for a type. */
  public Optional<NodeExecutor> get(String type) {
    return Optional.ofNullable(byType.get(type));
  }

  /** Registered types. */
  public Set<String> types() {
    return byType.keySet();
  }
}
