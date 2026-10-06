package com.relay.engine.template;

import static com.relay.testing.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.domain.catalog.NodeTypeSpec;
import com.relay.testing.Fixtures;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Satisfies: "Nodes reference earlier data with template expressions such as {@code
 * {{trigger.body.email}}} and {@code {{nodes.classify.output.category}}}, resolved by the engine."
 * — Case Study, Must Have §4; "An unresolvable path should fail the step with a clear error, not
 * silently render an empty string." — IMPLEMENTATION_GUIDE, FAQ.
 */
class TemplateResolverTest {

  private final TemplateResolver resolver = new TemplateResolver();

  private static NodeTypeSpec spec(String type) {
    return Fixtures.catalog().node(type).orElseThrow();
  }

  private static TemplateResolver.Scope scope(JsonNode body, Map<String, JsonNode> outputs) {
    return new TemplateResolver.Scope(body, id -> Optional.ofNullable(outputs.get(id)));
  }

  @Test
  void interpolatesTriggerBodyAndNodeOutputsIntoStrings() {
    ObjectNode params =
        json(
            """
            {"channel": "chat", "to": "#support",
             "message": "[{{nodes.classify.output.priority}}] {{nodes.classify.output.category}} on order {{trigger.body.order_id}}"}
            """);
    JsonNode out =
        resolver.resolve(
            spec("notify"),
            params,
            scope(
                json("{\"order_id\": \"ord_2001\"}"),
                Map.of("classify", json("{\"category\": \"complaint\", \"priority\": \"high\"}"))));
    assertThat(out.get("message").asText()).isEqualTo("[high] complaint on order ord_2001");
    assertThat(out.get("to").asText()).isEqualTo("#support");
  }

  @Test
  void numbersRenderAsTextInStringParams() {
    ObjectNode params = json("{\"left\": \"{{trigger.body.amount_usd}}\", \"op\": \"greater_than\", \"right\": \"100\"}");
    JsonNode out = resolver.resolve(spec("condition"), params, scope(json("{\"amount_usd\": 250}"), Map.of()));
    assertThat(out.get("left").isTextual()).isTrue();
    assertThat(out.get("left").asText()).isEqualTo("250");
  }

  @Test
  void dollarPrefixedTemplateFromTheSeed() {
    ObjectNode params = json("{\"message\": \"Expense of ${{trigger.body.amount_usd}} from {{trigger.body.employee_email}}\"}");
    JsonNode out =
        resolver.resolve(
            spec("approval"),
            params,
            scope(json("{\"amount_usd\": 250, \"employee_email\": \"dev1@example.com\"}"), Map.of()));
    assertThat(out.get("message").asText()).isEqualTo("Expense of $250 from dev1@example.com");
  }

  @Test
  void wholeTemplateInObjectParamKeepsItsJsonType() {
    ObjectNode params =
        json("{\"method\": \"POST\", \"url\": \"http://w/x\", \"body\": {\"order_id\": \"{{trigger.body.order_id}}\", \"n\": \"{{trigger.body.n}}\", \"label\": \"n={{trigger.body.n}}\"}}");
    JsonNode out =
        resolver.resolve(spec("http_request"), params, scope(json("{\"order_id\": \"ord_2003\", \"n\": 3}"), Map.of()));
    assertThat(out.at("/body/order_id").asText()).isEqualTo("ord_2003");
    assertThat(out.at("/body/n").isInt()).isTrue();
    assertThat(out.at("/body/label").asText()).isEqualTo("n=3");
  }

  @Test
  void nestedOutputPaths() {
    ObjectNode params = json("{\"channel\": \"email\", \"to\": \"a@b.c\", \"message\": \"Tracking: {{nodes.create_shipment.output.body.shipment_id}}\"}");
    JsonNode out =
        resolver.resolve(
            spec("notify"),
            params,
            scope(json("{}"), Map.of("create_shipment", json("{\"status\": 201, \"body\": {\"shipment_id\": \"shp_9\"}}"))));
    assertThat(out.get("message").asText()).isEqualTo("Tracking: shp_9");
  }

  @Test
  void missingTriggerFieldFailsClearlyNeverEmptyString() {
    ObjectNode params = json("{\"channel\": \"email\", \"to\": \"{{trigger.body.customer_email}}\", \"message\": \"hi\"}");
    assertThatThrownBy(() -> resolver.resolve(spec("notify"), params, scope(json("{}"), Map.of())))
        .isInstanceOf(TemplateResolutionException.class)
        .hasMessageContaining("{{trigger.body.customer_email}}")
        .hasMessageContaining("params.to")
        .hasMessageContaining("no field 'customer_email'");
  }

  @Test
  void referenceToANodeThatDidNotRunFailsClearly() {
    ObjectNode params = json("{\"channel\": \"email\", \"to\": \"a@b.c\", \"message\": \"Ref {{nodes.issue_refund.output.reference_id}}\"}");
    assertThatThrownBy(() -> resolver.resolve(spec("notify"), params, scope(json("{}"), Map.of())))
        .isInstanceOf(TemplateResolutionException.class)
        .hasMessageContaining("node 'issue_refund' has not produced output");
  }

  @Test
  void payloadTextIsNeverReScanned() {
    // An attacker-controlled body containing a template must be rendered literally.
    ObjectNode params = json("{\"channel\": \"chat\", \"to\": \"#support\", \"message\": \"{{trigger.body.message}}\"}");
    JsonNode out =
        resolver.resolve(
            spec("notify"),
            params,
            scope(json("{\"message\": \"{{nodes.secret.output.key}}\"}"), Map.of()));
    assertThat(out.get("message").asText()).isEqualTo("{{nodes.secret.output.key}}");
  }

  @Test
  void nonTemplatableParamsPassThroughUntouched() {
    ObjectNode params =
        json("{\"prompt\": \"Classify {{trigger.body.message}}\", \"output_schema\": {\"type\": \"object\", \"description\": \"{{literal}}\"}}");
    JsonNode out = resolver.resolve(spec("ai"), params, scope(json("{\"message\": \"hi\"}"), Map.of()));
    assertThat(out.get("prompt").asText()).isEqualTo("Classify hi");
    assertThat(out.at("/output_schema/description").asText()).isEqualTo("{{literal}}");
  }

  @Test
  void arrayIndexing() {
    ObjectNode params = json("{\"channel\": \"chat\", \"to\": \"#x\", \"message\": \"first={{trigger.body.items.0.sku}}\"}");
    JsonNode out =
        resolver.resolve(spec("notify"), params, scope(json("{\"items\": [{\"sku\": \"A1\"}]}"), Map.of()));
    assertThat(out.get("message").asText()).isEqualTo("first=A1");
  }
}
