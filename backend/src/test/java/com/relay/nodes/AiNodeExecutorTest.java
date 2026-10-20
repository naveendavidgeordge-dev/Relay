package com.relay.nodes;

import static com.relay.testing.Contexts.ctx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.domain.error.ErrorCode;
import com.relay.testing.FakeModel;
import com.relay.testing.Fixtures;
import com.relay.testing.TestProps;
import org.junit.jupiter.api.Test;

/**
 * Schema enforcement and the single repair retry, using {@code wf_support_triage.classify}'s real
 * {@code output_schema}.
 *
 * <p>Satisfies: "The engine validates the model's output against the schema, retries once with the
 * validation error included, and fails the step cleanly if the output still does not conform." —
 * Case Study, Must Have §5.
 */
class AiNodeExecutorTest {

  private static ObjectNode params() {
    ObjectNode classify = (ObjectNode) Fixtures.seed("wf_support_triage").get("nodes").get(0);
    ObjectNode p = ((ObjectNode) classify.get("params")).deepCopy();
    p.put("prompt", "Classify: my earbuds arrived cracked");
    return p;
  }

  private static final String VALID =
      "{\"category\": \"complaint\", \"priority\": \"high\", \"summary\": \"Cracked case\"}";

  @Test
  void validFirstAnswerIsAcceptedWithTokens() {
    FakeModel model = new FakeModel().reply(VALID);
    AiNodeExecutor ai = new AiNodeExecutor(model, TestProps.relay("http://w"));

    NodeResult.Completed r = (NodeResult.Completed) ai.execute(ctx("ai", "classify", params(), null));

    assertThat(r.output().path("category").asText()).isEqualTo("complaint");
    assertThat(r.tokens()).isEqualTo(new TokenUsage(40, 12));
    assertThat(model.requests).hasSize(1);
    assertThat(model.requests.get(0).repairFeedback()).isNull();
  }

  @Test
  void invalidAnswerIsRepairedOnceWithTheValidationErrorAppended() {
    FakeModel model =
        new FakeModel()
            .reply("{\"category\": \"refund please\", \"priority\": \"high\"}", VALID);
    AiNodeExecutor ai = new AiNodeExecutor(model, TestProps.relay("http://w"));

    NodeResult.Completed r = (NodeResult.Completed) ai.execute(ctx("ai", "classify", params(), null));

    assertThat(model.requests).hasSize(2);
    String feedback = model.requests.get(1).repairFeedback();
    assertThat(feedback).contains("category").contains("summary");
    assertThat(r.tokens()).as("both attempts are billed").isEqualTo(new TokenUsage(80, 24));
    assertThat(r.details()).hasSize(2);
    assertThat(r.details().get(0).path("valid").asBoolean()).isFalse();
    assertThat(r.details().get(0).path("validation_errors").size()).isGreaterThan(0);
    assertThat(r.details().get(1).path("kind").asText()).isEqualTo("repair");
    assertThat(r.details().get(1).path("valid").asBoolean()).isTrue();
  }

  @Test
  void nonJsonAnswerAlsoGetsOneRepair() {
    FakeModel model = new FakeModel().reply("Sure! The category is complaint.", "```json\n" + VALID + "\n```");
    NodeResult.Completed r =
        (NodeResult.Completed)
            new AiNodeExecutor(model, TestProps.relay("http://w"))
                .execute(ctx("ai", "classify", params(), null));
    assertThat(r.output().path("priority").asText()).isEqualTo("high");
  }

  @Test
  void stillInvalidAfterRepairFailsCleanlyWithoutMoreCalls() {
    FakeModel model =
        new FakeModel()
            .reply("{\"category\": \"refund_request\"}", "{\"category\": \"refund_request\", \"extra\": 1}", VALID);
    AiNodeExecutor ai = new AiNodeExecutor(model, TestProps.relay("http://w"));

    assertThatThrownBy(() -> ai.execute(ctx("ai", "classify", params(), null)))
        .isInstanceOf(NodeFailureException.class)
        .satisfies(
            e -> {
              NodeFailureException f = (NodeFailureException) e;
              assertThat(f.code()).isEqualTo(ErrorCode.AI_SCHEMA_VIOLATION);
              assertThat(f.details()).containsKeys("first_attempt_errors", "repair_attempt_errors", "tokens_prompt");
            });
    assertThat(model.requests).as("exactly one repair, never a third call").hasSize(2);
  }

  @Test
  void schemaViolationIsNotATransientError() {
    // NodeInvoker only retries TransientNodeException; a schema failure must not burn retries.
    assertThat(TransientNodeException.class.isAssignableFrom(NodeFailureException.class)).isFalse();
  }
}
