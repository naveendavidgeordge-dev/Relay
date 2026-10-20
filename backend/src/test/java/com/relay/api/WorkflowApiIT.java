package com.relay.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.testing.Fixtures;
import com.relay.testing.PostgresContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end over HTTP against Postgres: seeding, the fixed workflow routes, publish validation
 * and secret hygiene. The worker is off ({@code api} profile) — nothing here executes nodes.
 *
 * <p>Satisfies: "Load the node catalog and the four seed workflows as Published." and "Create,
 * list, publish workflows in the seed JSON shape; publish validation rejects broken definitions" —
 * IMPLEMENTATION_GUIDE, Must Have Checklist.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("api")
class WorkflowApiIT extends PostgresContainerSupport {

  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;

  private ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body) {
    HttpHeaders h = new HttpHeaders();
    h.setBearerAuth("relay-demo-token");
    h.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(path, method, new HttpEntity<>(body, h), JsonNode.class);
  }

  @Test
  void seedsAreListedAsPublished() {
    ResponseEntity<JsonNode> res = call(HttpMethod.GET, "/workflows", null);
    assertThat(res.getStatusCode().value()).isEqualTo(200);
    assertThat(res.getBody().isArray()).isTrue();
    for (String id : Fixtures.seedIds()) {
      JsonNode wf = find(res.getBody(), id);
      assertThat(wf).as(id).isNotNull();
      assertThat(wf.path("status").asText()).isEqualTo("published");
      assertThat(wf.path("published_version").asInt()).isEqualTo(1);
    }
  }

  @Test
  void webhookSecretsNeverLeaveTheServer() {
    ResponseEntity<JsonNode> res = call(HttpMethod.GET, "/workflows/wf_expense_approval", null);
    assertThat(res.getBody().at("/definition/trigger/secret").asText()).isEqualTo("********");
    assertThat(res.getBody().toString()).doesNotContain("whsec_expense_774");
    assertThat(call(HttpMethod.GET, "/workflows", null).getBody().toString())
        .doesNotContain("whsec_");
    String stored =
        jdbc.queryForObject(
            "SELECT definition_json::text FROM workflows WHERE id = 'wf_expense_approval'",
            String.class);
    assertThat(stored).doesNotContain("whsec_expense_774");
  }

  @Test
  void publishedVersionsAreSignedWithMlDsa() {
    JsonNode wf = call(HttpMethod.GET, "/workflows/wf_support_triage", null).getBody();
    JsonNode v1 = wf.path("versions").get(0);
    assertThat(v1.path("signature_algorithm").asText()).isEqualTo("ML-DSA-65");
    assertThat(v1.path("signature_valid").asBoolean()).isTrue();
  }

  @Test
  void createPublishHappyPath() {
    String id = "wf_it_" + UUID.randomUUID().toString().substring(0, 8);
    ObjectNode def =
        Fixtures.json(
            """
            {"id": "%s", "name": "Smoke minimal", "trigger": {"type": "manual"}, "entry": "hello",
             "limits": {"max_steps": 5, "timeout_seconds": 60, "max_ai_tokens": 0},
             "nodes": [{"id": "hello", "type": "notify",
                        "params": {"channel": "chat", "to": "#smoke", "message": "smoke"},
                        "next": null}]}
            """
                .formatted(id));
    ResponseEntity<JsonNode> created = call(HttpMethod.POST, "/workflows", def);
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    assertThat(created.getBody().path("status").asText()).isEqualTo("draft");

    ResponseEntity<JsonNode> published = call(HttpMethod.POST, "/workflows/" + id + "/publish", null);
    assertThat(published.getStatusCode().value()).isEqualTo(200);
    assertThat(published.getBody().path("status").asText()).isEqualTo("published");
  }

  @Test
  void theSmokeTestsThreeBrokenDefinitionsAreRejectedWithDistinctCodes() {
    String[][] cases = {
      {"{\"id\": \"n1\", \"type\": \"teleport\", \"params\": {}, \"next\": null}", "invalid_node_type"},
      {"{\"id\": \"n1\", \"type\": \"notify\", \"params\": {\"channel\": \"chat\"}, \"next\": null}",
          "missing_required_param"},
      {"{\"id\": \"n1\", \"type\": \"delay\", \"params\": {\"seconds\": 1}, \"next\": \"ghost\"}",
          "invalid_edge_target"}
    };
    for (String[] c : cases) {
      String id = "wf_bad_" + UUID.randomUUID().toString().substring(0, 8);
      ObjectNode def =
          Fixtures.json(
              """
              {"id": "%s", "name": "bad", "trigger": {"type": "manual"}, "entry": "n1",
               "limits": {"max_steps": 5, "timeout_seconds": 60, "max_ai_tokens": 0},
               "nodes": [%s]}
              """
                  .formatted(id, c[0]));
      assertThat(call(HttpMethod.POST, "/workflows", def).getStatusCode().value()).isEqualTo(201);
      ResponseEntity<JsonNode> res = call(HttpMethod.POST, "/workflows/" + id + "/publish", null);
      assertThat(res.getStatusCode().value()).as(c[1]).isEqualTo(422);
      assertThat(res.getBody().at("/error/code").asText()).isEqualTo(c[1]);
    }
  }

  @Test
  void editingAPublishedWorkflowNeverTouchesItsFrozenVersion() {
    String id = "wf_edit_" + UUID.randomUUID().toString().substring(0, 8);
    ObjectNode def = Fixtures.seed("wf_slow_fulfillment");
    def.put("id", id);
    call(HttpMethod.POST, "/workflows", def);
    call(HttpMethod.POST, "/workflows/" + id + "/publish", null);

    ((ObjectNode) def.get("nodes").get(1).get("params")).put("seconds", 1);
    ResponseEntity<JsonNode> updated = call(HttpMethod.PUT, "/workflows/" + id, def);
    assertThat(updated.getStatusCode().value()).isEqualTo(200);
    assertThat(updated.getBody().path("has_unpublished_changes").asBoolean()).isTrue();
    assertThat(updated.getBody().at("/definition/nodes/1/params/seconds").asInt()).isEqualTo(1);
    assertThat(updated.getBody().at("/published_definition/nodes/1/params/seconds").asInt())
        .isEqualTo(20);

    JsonNode republished = call(HttpMethod.POST, "/workflows/" + id + "/publish", null).getBody();
    assertThat(republished.path("published_version").asInt()).isEqualTo(2);
  }

  @Test
  void webhookSecretIsEnforcedAndTriggersOnlyEnqueue() {
    String body = "{\"employee_email\": \"dev2@example.com\", \"amount_usd\": 40, \"description\": \"x\"}";
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    assertThat(
            http.exchange("/hooks/wf_expense_approval", HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class)
                .getStatusCode()
                .value())
        .isEqualTo(401);
    h.set("X-Relay-Secret", "wrong-secret");
    ResponseEntity<JsonNode> wrong =
        http.exchange("/hooks/wf_expense_approval", HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    assertThat(wrong.getStatusCode().value()).isEqualTo(403);
    assertThat(wrong.getBody().toString()).doesNotContain("whsec_").doesNotContain("wrong-secret");

    h.set("X-Relay-Secret", "whsec_expense_774");
    ResponseEntity<JsonNode> ok =
        http.exchange("/hooks/wf_expense_approval", HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    assertThat(ok.getStatusCode().value()).isEqualTo(202);
    String runId = ok.getBody().path("run_id").asText();
    assertThat(runId).startsWith("run_");

    JsonNode run = call(HttpMethod.GET, "/runs/" + runId, null).getBody();
    assertThat(run.path("status").asText()).as("api profile has no worker").isEqualTo("queued");
    assertThat(run.path("steps").isArray()).isTrue();
    assertThat(run.path("signature_valid").asBoolean()).isTrue();
    assertThat(run.path("definition_snapshot").toString()).doesNotContain("whsec_");
  }

  @Test
  void manualTriggerOfADraftIs409() {
    String id = "wf_draft_" + UUID.randomUUID().toString().substring(0, 8);
    ObjectNode def = Fixtures.seed("wf_runaway");
    def.put("id", id);
    call(HttpMethod.POST, "/workflows", def);
    ResponseEntity<JsonNode> res =
        call(HttpMethod.POST, "/workflows/" + id + "/trigger", Fixtures.json("{\"input\": {}}"));
    assertThat(res.getStatusCode().value()).isEqualTo(409);
    assertThat(res.getBody().at("/error/code").asText()).isEqualTo("workflow_not_published");
  }

  @Test
  void unknownRunAndApprovalAre404() {
    assertThat(call(HttpMethod.GET, "/runs/run_nope", null).getStatusCode().value()).isEqualTo(404);
    assertThat(call(HttpMethod.POST, "/approvals/apr_nope/approve", null).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void unknownWorkflowIs404() {
    ResponseEntity<JsonNode> res = call(HttpMethod.GET, "/workflows/wf_nope", null);
    assertThat(res.getStatusCode().value()).isEqualTo(404);
    assertThat(res.getBody().at("/error/code").asText()).isEqualTo("workflow_not_found");
  }

  @Test
  void duplicateCreateIs409() {
    ResponseEntity<JsonNode> res = call(HttpMethod.POST, "/workflows", Fixtures.seed("wf_runaway"));
    assertThat(res.getStatusCode().value()).isEqualTo(409);
    assertThat(res.getBody().at("/error/code").asText()).isEqualTo("workflow_exists");
  }

  private static JsonNode find(JsonNode list, String id) {
    for (JsonNode n : list) {
      if (id.equals(n.path("id").asText())) {
        return n;
      }
    }
    return null;
  }
}
