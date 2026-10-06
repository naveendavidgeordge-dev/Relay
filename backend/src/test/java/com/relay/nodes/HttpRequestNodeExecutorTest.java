package com.relay.nodes;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.relay.testing.Contexts.ctx;
import static com.relay.testing.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.relay.config.RelayProperties;
import com.relay.domain.error.ErrorCode;
import com.relay.integrations.http.OutboundHttp;
import com.relay.testing.TestProps;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Satisfies: "Mutating requests (non-GET) are side effects and must carry an Idempotency-Key
 * header." — node_catalog.json, http_request; "Every external call … needs a timeout" — Case
 * Study, Technical Requirements.
 */
class HttpRequestNodeExecutorTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private HttpRequestNodeExecutor executor(RelayProperties props) {
    return new HttpRequestNodeExecutor(
        new OutboundHttp(props, CircuitBreakerRegistry.ofDefaults(), new ObjectMapper()));
  }

  private HttpRequestNodeExecutor executor() {
    return executor(TestProps.relay(wm.baseUrl()));
  }

  @Test
  void getIsAReadAndCarriesNoIdempotencyKey() {
    wm.stubFor(get(urlEqualTo("/orders/ord_2001")).willReturn(okJson("{\"status\":\"processing\"}")));
    var params = json("{\"method\": \"GET\", \"url\": \"" + wm.baseUrl() + "/orders/ord_2001\"}");

    NodeResult.Completed r = (NodeResult.Completed) executor().execute(ctx("http_request", "check_order", params, null));

    assertThat(r.output().path("status").asInt()).isEqualTo(200);
    assertThat(r.output().path("body").path("status").asText()).isEqualTo("processing");
    wm.verify(getRequestedFor(urlEqualTo("/orders/ord_2001")).withHeader("Idempotency-Key", absent()));
  }

  @Test
  void postCarriesTheIdempotencyKeyAndJsonBody() {
    wm.stubFor(
        post(urlEqualTo("/shipments"))
            .willReturn(okJson("{\"shipment_id\":\"shp_1\",\"status\":\"created\"}").withStatus(201)));
    var params =
        json(
            "{\"method\": \"POST\", \"url\": \"" + wm.baseUrl() + "/shipments\","
                + " \"body\": {\"order_id\": \"ord_2003\"}}");

    NodeResult.Completed r =
        (NodeResult.Completed)
            executor().execute(ctx("http_request", "create_shipment", params, "run_test:create_shipment"));

    assertThat(r.output().path("status").asInt()).isEqualTo(201);
    assertThat(r.output().path("body").path("shipment_id").asText()).isEqualTo("shp_1");
    wm.verify(
        postRequestedFor(urlEqualTo("/shipments"))
            .withHeader("Idempotency-Key", equalTo("run_test:create_shipment"))
            .withRequestBody(equalToJson("{\"order_id\": \"ord_2003\"}")));
  }

  @Test
  void definitionCannotOverrideTheEngineKey() {
    wm.stubFor(post(urlEqualTo("/x")).willReturn(okJson("{}")));
    var params =
        json(
            "{\"method\": \"POST\", \"url\": \"" + wm.baseUrl() + "/x\","
                + " \"headers\": {\"Idempotency-Key\": \"attacker\", \"X-Trace\": \"t1\"}}");
    executor().execute(ctx("http_request", "n", params, "run_test:n"));
    wm.verify(
        postRequestedFor(urlEqualTo("/x"))
            .withHeader("Idempotency-Key", equalTo("run_test:n"))
            .withHeader("X-Trace", equalTo("t1")));
  }

  @Test
  void serverErrorsAreTransient() {
    wm.stubFor(post(urlEqualTo("/shipments")).willReturn(aResponse().withStatus(503)));
    var params = json("{\"method\": \"POST\", \"url\": \"" + wm.baseUrl() + "/shipments\"}");
    assertThatThrownBy(() -> executor().execute(ctx("http_request", "n", params, "k")))
        .isInstanceOf(TransientNodeException.class)
        .hasMessageContaining("503");
  }

  @Test
  void clientErrorsCompleteSoWorkflowsCanBranchOnStatus() {
    wm.stubFor(get(urlEqualTo("/status")).willReturn(aResponse().withStatus(404).withBody("nope")));
    var params = json("{\"method\": \"GET\", \"url\": \"" + wm.baseUrl() + "/status\"}");
    NodeResult.Completed r = (NodeResult.Completed) executor().execute(ctx("http_request", "n", params, null));
    assertThat(r.output().path("status").asInt()).isEqualTo(404);
    assertThat(r.output().path("body").asText()).isEqualTo("nope");
  }

  @Test
  void hungDependencyTimesOutAsTransient() {
    wm.stubFor(get(urlEqualTo("/slow")).willReturn(okJson("{}").withFixedDelay(1500)));
    var params = json("{\"method\": \"GET\", \"url\": \"" + wm.baseUrl() + "/slow\"}");
    HttpRequestNodeExecutor fast = executor(TestProps.relay(wm.baseUrl(), "", "", 300));
    assertThatThrownBy(() -> fast.execute(ctx("http_request", "n", params, null)))
        .isInstanceOf(TransientNodeException.class)
        .extracting(e -> ((TransientNodeException) e).code())
        .isEqualTo(ErrorCode.NODE_TIMEOUT);
  }

  @Test
  void urlAliasRewritesOnlyTheTargetAndIsTraced() {
    wm.stubFor(get(urlEqualTo("/orders/ord_2001")).willReturn(okJson("{\"status\":\"processing\"}")));
    // the seed hardcodes localhost:9210; in docker the alias points it at the mock-world service
    var params = json("{\"method\": \"GET\", \"url\": \"http://localhost:9210/orders/ord_2001\"}");
    HttpRequestNodeExecutor aliased =
        executor(TestProps.relay(wm.baseUrl(), "http://localhost:9210=>" + wm.baseUrl(), "", 1000));
    NodeResult.Completed r = (NodeResult.Completed) aliased.execute(ctx("http_request", "n", params, null));
    assertThat(r.output().path("status").asInt()).isEqualTo(200);
    assertThat(r.details()).singleElement()
        .satisfies(d -> assertThat(d.get("effective_url").asText()).startsWith(wm.baseUrl()));
  }

  @Test
  void hostAllowlistBlocksOtherHosts() {
    var params = json("{\"method\": \"GET\", \"url\": \"http://169.254.169.254/latest/meta-data\"}");
    HttpRequestNodeExecutor guarded = executor(TestProps.relay(wm.baseUrl(), "", "localhost", 1000));
    assertThatThrownBy(() -> guarded.execute(ctx("http_request", "n", params, null)))
        .isInstanceOf(NodeFailureException.class)
        .hasMessageContaining("RELAY_HTTP_ALLOWED_HOSTS");
  }

  @Test
  void nonHttpSchemesAreRejected() {
    var params = json("{\"method\": \"GET\", \"url\": \"file:///etc/passwd\"}");
    assertThatThrownBy(() -> executor().execute(ctx("http_request", "n", params, null)))
        .isInstanceOf(NodeFailureException.class);
  }
}
