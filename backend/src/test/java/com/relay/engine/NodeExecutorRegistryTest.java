package com.relay.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.integrations.http.OutboundHttp;
import com.relay.integrations.mockworld.MockWorldClient;
import com.relay.nodes.AiNodeExecutor;
import com.relay.nodes.ApprovalNodeExecutor;
import com.relay.nodes.ConditionNodeExecutor;
import com.relay.nodes.DelayNodeExecutor;
import com.relay.nodes.HttpRequestNodeExecutor;
import com.relay.nodes.NodeExecutionContext;
import com.relay.nodes.NodeExecutor;
import com.relay.nodes.NodeResult;
import com.relay.nodes.NotifyNodeExecutor;
import com.relay.nodes.OrderActionNodeExecutor;
import com.relay.testing.FakeModel;
import com.relay.testing.Fixtures;
import com.relay.testing.TestProps;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** "Never emit a node type outside node_catalog.json" — enforced in both directions at startup. */
class NodeExecutorRegistryTest {

  private static List<NodeExecutor> all() {
    var props = TestProps.relay("http://w");
    var http = new OutboundHttp(props, CircuitBreakerRegistry.ofDefaults(), new ObjectMapper());
    var world = new MockWorldClient(http, props);
    return new ArrayList<>(
        List.of(
            new HttpRequestNodeExecutor(http),
            new ConditionNodeExecutor(),
            new DelayNodeExecutor(Clock.systemUTC()),
            new NotifyNodeExecutor(world),
            new AiNodeExecutor(new FakeModel(), props),
            new ApprovalNodeExecutor(),
            new OrderActionNodeExecutor(world)));
  }

  @Test
  void everyCatalogTypeHasExactlyOneExecutor() {
    NodeExecutorRegistry r = new NodeExecutorRegistry(all(), Fixtures.catalog());
    assertThat(r.types()).containsExactlyInAnyOrderElementsOf(Fixtures.catalog().nodeTypes());
  }

  @Test
  void anExecutorForATypeOutsideTheCatalogIsFatal() {
    List<NodeExecutor> executors = all();
    executors.add(
        new NodeExecutor() {
          @Override
          public String type() {
            return "teleport";
          }

          @Override
          public NodeResult execute(NodeExecutionContext ctx) {
            throw new UnsupportedOperationException();
          }
        });
    assertThatThrownBy(() -> new NodeExecutorRegistry(executors, Fixtures.catalog()))
        .hasMessageContaining("teleport");
  }

  @Test
  void aMissingExecutorIsFatal() {
    List<NodeExecutor> executors = all();
    executors.removeIf(e -> e.type().equals("order_action"));
    assertThatThrownBy(() -> new NodeExecutorRegistry(executors, Fixtures.catalog()))
        .hasMessageContaining("order_action");
  }
}
