package com.relay.nodes;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
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
import com.relay.integrations.mockworld.MockWorldClient;
import com.relay.testing.TestProps;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Satisfies: "channel 'email' -> POST {MOCK_WORLD_URL}/email/send, channel 'chat' -> POST
 * {MOCK_WORLD_URL}/chat/message. Side effect: must carry an Idempotency-Key." — node_catalog.json,
 * notify. The JSON bodies match what {@code scripts/mock_world.py} validates and ledgers.
 */
class NotifyNodeExecutorTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private NotifyNodeExecutor executor;

  @BeforeEach
  void setUp() {
    RelayProperties props = TestProps.relay(wm.baseUrl());
    OutboundHttp http = new OutboundHttp(props, CircuitBreakerRegistry.ofDefaults(), new ObjectMapper());
    executor = new NotifyNodeExecutor(new MockWorldClient(http, props));
  }

  @Test
  void emailGoesToEmailSendWithKeyAndSubject() {
    wm.stubFor(
        post(urlEqualTo("/email/send"))
            .willReturn(okJson("{\"delivered\": true, \"notification_id\": \"eml_1\"}")));
    var params =
        json(
            "{\"channel\": \"email\", \"to\": \"lena@example.com\", \"subject\": \"Order received\","
                + " \"message\": \"We received your order ord_2003\"}");

    NodeResult.Completed r =
        (NodeResult.Completed) executor.execute(ctx("notify", "confirm", params, "run_x:confirm"));

    assertThat(r.output().path("delivered").asBoolean()).isTrue();
    assertThat(r.output().path("notification_id").asText()).isEqualTo("eml_1");
    wm.verify(
        postRequestedFor(urlEqualTo("/email/send"))
            .withHeader("Idempotency-Key", equalTo("run_x:confirm"))
            .withRequestBody(
                equalToJson(
                    "{\"to\": \"lena@example.com\", \"subject\": \"Order received\","
                        + " \"message\": \"We received your order ord_2003\"}")));
  }

  @Test
  void chatGoesToChatMessageWithChannel() {
    wm.stubFor(
        post(urlEqualTo("/chat/message"))
            .willReturn(okJson("{\"delivered\": true, \"notification_id\": \"msg_1\"}")));
    var params = json("{\"channel\": \"chat\", \"to\": \"#smoke\", \"message\": \"smoke wf_1\"}");

    executor.execute(ctx("notify", "hello", params, "run_x:hello"));

    wm.verify(
        postRequestedFor(urlEqualTo("/chat/message"))
            .withHeader("Idempotency-Key", equalTo("run_x:hello"))
            .withRequestBody(equalToJson("{\"channel\": \"#smoke\", \"message\": \"smoke wf_1\"}")));
  }

  @Test
  void replayIsRecordedInTheTraceDetails() {
    wm.stubFor(
        post(urlEqualTo("/email/send"))
            .willReturn(
                okJson("{\"delivered\": true, \"notification_id\": \"eml_1\"}")
                    .withHeader("x-mockworld-replayed", "true")));
    var params = json("{\"channel\": \"email\", \"to\": \"a@b.c\", \"message\": \"m\"}");
    NodeResult.Completed r = (NodeResult.Completed) executor.execute(ctx("notify", "n", params, "k"));
    assertThat(r.details()).singleElement().satisfies(d -> assertThat(d.get("replayed").asBoolean()).isTrue());
  }

  @Test
  void businessRejectionIsPermanent() {
    wm.stubFor(
        post(urlEqualTo("/email/send"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\": {\"message\": \"Missing field 'to'\"}}")));
    var params = json("{\"channel\": \"email\", \"to\": \"x\", \"message\": \"m\"}");
    assertThatThrownBy(() -> executor.execute(ctx("notify", "n", params, "k")))
        .isInstanceOf(NodeFailureException.class)
        .hasMessageContaining("Missing field 'to'")
        .extracting(e -> ((NodeFailureException) e).code())
        .isEqualTo(ErrorCode.UPSTREAM_REJECTED);
  }

  @Test
  void worldDownIsTransient() {
    wm.stubFor(post(urlEqualTo("/chat/message")).willReturn(aResponse().withStatus(503)));
    var params = json("{\"channel\": \"chat\", \"to\": \"#ops\", \"message\": \"m\"}");
    assertThatThrownBy(() -> executor.execute(ctx("notify", "n", params, "k")))
        .isInstanceOf(TransientNodeException.class);
  }

  @Test
  void refusesToSendWithoutAKey() {
    var params = json("{\"channel\": \"chat\", \"to\": \"#ops\", \"message\": \"m\"}");
    assertThatThrownBy(() -> executor.execute(ctx("notify", "n", params, null)))
        .isInstanceOf(NodeFailureException.class);
  }
}
