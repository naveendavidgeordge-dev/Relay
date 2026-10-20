package com.relay.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.domain.error.ErrorCode;
import com.relay.testing.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Publish validation against the real catalog.
 *
 * <p>Satisfies: "Publish-time validation must reject, with a distinct message per case: a node
 * {@code type} not present in {@code data/node_catalog.json}, a missing required param (per the
 * catalog), a {@code next}/{@code on_true}/{@code on_false} pointing to a nonexistent node, an
 * {@code entry} pointing to a nonexistent node. Backward jumps (loops) are legal and must not be
 * rejected" — API_CONTRACT §1.
 */
class PublishValidatorTest {

  private final PublishValidator validator = new PublishValidator(Fixtures.catalog(), false);

  /** The same minimal wrapper the smoke test uses for its broken definitions. */
  private static ObjectNode wrap(String nodeJson) {
    return Fixtures.json(
        """
        {"id": "wf_bad", "name": "bad", "trigger": {"type": "manual"}, "entry": "n1",
         "limits": {"max_steps": 5, "timeout_seconds": 60, "max_ai_tokens": 0},
         "nodes": [%s]}
        """
            .formatted(nodeJson));
  }

  private static ErrorCode firstError(ValidationReport r) {
    assertThat(r.isValid()).as("expected a blocking error, got %s", r.violations()).isFalse();
    return r.errors().get(0).code();
  }

  static String[] seedIds() {
    return Fixtures.seedIds();
  }

  @ParameterizedTest(name = "seed {0} is valid")
  @MethodSource("seedIds")
  void everySeedWorkflowPublishes(String id) {
    ValidationReport r = validator.validate(Fixtures.seed(id));
    assertThat(r.errors()).isEmpty();
    assertThat(r.warnings()).isEmpty();
  }

  @Nested
  @DisplayName("the four contract rejection cases (each with a distinct code)")
  class ContractCases {

    @Test
    void unknownNodeType() {
      ValidationReport r =
          validator.validate(wrap("{\"id\": \"n1\", \"type\": \"teleport\", \"params\": {}, \"next\": null}"));
      assertThat(firstError(r)).isEqualTo(ErrorCode.INVALID_NODE_TYPE);
      assertThat(r.errors().get(0).message()).isEqualTo("Node 'n1' has unknown type 'teleport'");
    }

    @Test
    void missingRequiredParam() {
      ValidationReport r =
          validator.validate(
              wrap("{\"id\": \"n1\", \"type\": \"notify\", \"params\": {\"channel\": \"chat\"}, \"next\": null}"));
      assertThat(firstError(r)).isEqualTo(ErrorCode.MISSING_REQUIRED_PARAM);
      assertThat(r.errors())
          .extracting(Violation::field)
          .containsExactly("params.to", "params.message");
    }

    @Test
    void edgeToNonexistentNode() {
      ValidationReport r =
          validator.validate(
              wrap("{\"id\": \"n1\", \"type\": \"delay\", \"params\": {\"seconds\": 1}, \"next\": \"ghost\"}"));
      assertThat(firstError(r)).isEqualTo(ErrorCode.INVALID_EDGE_TARGET);
      assertThat(r.errors().get(0).message()).contains("ghost");
      assertThat(r.violations()).noneMatch(v -> v.code() == ErrorCode.CYCLIC_ENTRY);
    }

    @Test
    void conditionBranchToNonexistentNode() {
      ObjectNode def = Fixtures.seed("wf_expense_approval");
      ((ObjectNode) def.get("nodes").get(0)).put("on_false", "nowhere");
      assertThat(firstError(validator.validate(def))).isEqualTo(ErrorCode.INVALID_EDGE_TARGET);
    }

    @Test
    void entryToNonexistentNode() {
      ObjectNode def = Fixtures.seed("wf_slow_fulfillment");
      def.put("entry", "nope");
      ValidationReport r = validator.validate(def);
      assertThat(firstError(r)).isEqualTo(ErrorCode.INVALID_ENTRY);
      assertThat(r.errors().get(0).message()).isEqualTo("Entry 'nope' does not match any node");
    }

    @Test
    void theFourCodesAreDistinct() {
      assertThat(
              java.util.Set.of(
                  ErrorCode.INVALID_NODE_TYPE,
                  ErrorCode.MISSING_REQUIRED_PARAM,
                  ErrorCode.INVALID_EDGE_TARGET,
                  ErrorCode.INVALID_ENTRY))
          .hasSize(4);
    }
  }

  @Nested
  @DisplayName("loops are legal")
  class Loops {

    @Test
    void runawayPollLoopIsValid() {
      assertThat(validator.validate(Fixtures.seed("wf_runaway")).isValid()).isTrue();
    }

    @Test
    void selfLoopWithAnExitIsValid() {
      ObjectNode def =
          Fixtures.json(
              """
              {"id": "wf_self", "name": "self", "trigger": {"type": "manual"}, "entry": "check",
               "limits": {"max_steps": 10},
               "nodes": [
                 {"id": "check", "type": "condition",
                  "params": {"left": "{{trigger.body.x}}", "op": "equals", "right": "done"},
                  "on_true": null, "on_false": "check"}
               ]}
              """);
      ValidationReport r = validator.validate(def);
      assertThat(r.isValid()).isTrue();
      assertThat(r.warnings()).isEmpty();
    }

    @Test
    void loopWithNoExitIsOnlyAWarningByDefault() {
      ObjectNode def = noExitLoop();
      ValidationReport r = validator.validate(def);
      assertThat(r.isValid()).isTrue();
      assertThat(r.warnings()).extracting(Violation::code).contains(ErrorCode.CYCLIC_ENTRY);
    }

    @Test
    void loopWithNoExitIsRejectedWhenConfigured() {
      PublishValidator strict = new PublishValidator(Fixtures.catalog(), true);
      assertThat(firstError(strict.validate(noExitLoop()))).isEqualTo(ErrorCode.CYCLIC_ENTRY);
    }

    private ObjectNode noExitLoop() {
      return Fixtures.json(
          """
          {"id": "wf_forever", "name": "forever", "trigger": {"type": "manual"}, "entry": "a",
           "limits": {"max_steps": 10},
           "nodes": [
             {"id": "a", "type": "delay", "params": {"seconds": 1}, "next": "b"},
             {"id": "b", "type": "delay", "params": {"seconds": 1}, "next": "a"}
           ]}
          """);
    }
  }

  @Nested
  @DisplayName("other structural rules")
  class OtherRules {

    @Test
    void emptyNodes() {
      ObjectNode def = Fixtures.seed("wf_runaway");
      def.set("nodes", Fixtures.MAPPER.createArrayNode());
      ValidationReport r = validator.validate(def);
      assertThat(firstError(r)).isEqualTo(ErrorCode.EMPTY_NODES);
      assertThat(r.errors()).hasSize(1);
    }

    @Test
    void duplicateNodeIds() {
      ObjectNode def = Fixtures.seed("wf_runaway");
      ((ArrayNode) def.get("nodes")).add(def.get("nodes").get(0).deepCopy());
      assertThat(firstError(validator.validate(def))).isEqualTo(ErrorCode.DUPLICATE_NODE_IDS);
    }

    @Test
    void enumViolation() {
      ValidationReport r =
          validator.validate(
              wrap(
                  "{\"id\": \"n1\", \"type\": \"notify\", \"params\": {\"channel\": \"sms\","
                      + " \"to\": \"+1555\", \"message\": \"hi\"}, \"next\": null}"));
      assertThat(firstError(r)).isEqualTo(ErrorCode.INVALID_PARAM_VALUE);
      assertThat(r.errors().get(0).message()).contains("sms").contains("[email, chat]");
    }

    @Test
    void wrongParamType() {
      ValidationReport r =
          validator.validate(
              wrap("{\"id\": \"n1\", \"type\": \"delay\", \"params\": {\"seconds\": \"soon\"}, \"next\": null}"));
      assertThat(firstError(r)).isEqualTo(ErrorCode.INVALID_PARAM_VALUE);
    }

    @Test
    void conditionMustNotUseNext() {
      ObjectNode def = Fixtures.seed("wf_expense_approval");
      ((ObjectNode) def.get("nodes").get(0)).put("next", "auto_ok");
      assertThat(firstError(validator.validate(def))).isEqualTo(ErrorCode.INVALID_EDGE);
    }

    @Test
    void nonConditionMustNotBranch() {
      ObjectNode def = Fixtures.seed("wf_slow_fulfillment");
      ((ObjectNode) def.get("nodes").get(0)).put("on_true", "pack_delay");
      assertThat(firstError(validator.validate(def))).isEqualTo(ErrorCode.INVALID_EDGE);
    }

    @Test
    void templateReferencingUnknownNode() {
      ObjectNode def = Fixtures.seed("wf_support_triage");
      ObjectNode route = (ObjectNode) def.get("nodes").get(1);
      ((ObjectNode) route.get("params")).put("left", "{{nodes.clasify.output.category}}");
      ValidationReport r = validator.validate(def);
      assertThat(firstError(r)).isEqualTo(ErrorCode.INVALID_TEMPLATE);
      assertThat(r.errors().get(0).message()).contains("clasify");
    }

    @Test
    void templateOutsideGrammar() {
      ObjectNode def = Fixtures.seed("wf_support_triage");
      ObjectNode route = (ObjectNode) def.get("nodes").get(1);
      ((ObjectNode) route.get("params")).put("left", "{{env.SECRET_KEY}}");
      assertThat(firstError(validator.validate(def))).isEqualTo(ErrorCode.INVALID_TEMPLATE);
    }

    @Test
    void unknownTriggerType() {
      ObjectNode def = Fixtures.seed("wf_runaway");
      ((ObjectNode) def.get("trigger")).put("type", "email_inbox");
      assertThat(firstError(validator.validate(def))).isEqualTo(ErrorCode.INVALID_TRIGGER);
    }

    @Test
    void webhookWithoutSecret() {
      ObjectNode def = Fixtures.seed("wf_expense_approval");
      ((ObjectNode) def.get("trigger")).remove("secret");
      assertThat(firstError(validator.validate(def))).isEqualTo(ErrorCode.INVALID_TRIGGER);
    }

    @Test
    void scheduleWithBadCron() {
      ObjectNode def = Fixtures.seed("wf_runaway");
      def.set("trigger", Fixtures.json("{\"type\": \"schedule\", \"cron\": \"every morning\"}"));
      assertThat(firstError(validator.validate(def))).isEqualTo(ErrorCode.INVALID_TRIGGER);
    }

    @Test
    void scheduleWithGoodCron() {
      ObjectNode def = Fixtures.seed("wf_runaway");
      def.set("trigger", Fixtures.json("{\"type\": \"schedule\", \"cron\": \"0 8 * * *\"}"));
      assertThat(validator.validate(def).isValid()).isTrue();
    }

    @Test
    void maxStepsMustBePositive() {
      ObjectNode def = Fixtures.seed("wf_runaway");
      ((ObjectNode) def.get("limits")).put("max_steps", 0);
      assertThat(firstError(validator.validate(def))).isEqualTo(ErrorCode.INVALID_LIMITS);
    }

    @Test
    void notAnObject() {
      ValidationReport r = validator.validate(Fixtures.MAPPER.createArrayNode());
      assertThat(firstError(r)).isEqualTo(ErrorCode.INVALID_DEFINITION);
    }
  }

  @Nested
  @DisplayName("approval precedence is advisory at publish (the engine enforces it)")
  class ApprovalPrecedence {

    @Test
    void orderActionWithoutApprovalWarnsButPublishes() {
      ObjectNode def =
          Fixtures.json(
              """
              {"id": "wf_bypass", "name": "bypass", "trigger": {"type": "manual"}, "entry": "refund",
               "limits": {"max_steps": 5},
               "nodes": [
                 {"id": "refund", "type": "order_action",
                  "params": {"action": "refund", "order_id": "ord_2002"}, "next": null}
               ]}
              """);
      ValidationReport r = validator.validate(def);
      assertThat(r.isValid()).isTrue();
      assertThat(r.warnings())
          .extracting(Violation::code)
          .containsExactly(ErrorCode.APPROVAL_NOT_GUARANTEED);
    }

    @Test
    void approvalOnTheOtherBranchStillWarns() {
      ObjectNode def =
          Fixtures.json(
              """
              {"id": "wf_other_branch", "name": "x", "trigger": {"type": "manual"}, "entry": "c",
               "limits": {"max_steps": 5},
               "nodes": [
                 {"id": "c", "type": "condition",
                  "params": {"left": "{{trigger.body.a}}", "op": "equals", "right": "1"},
                  "on_true": "gate", "on_false": "refund"},
                 {"id": "gate", "type": "approval", "params": {"message": "ok?"}, "next": null},
                 {"id": "refund", "type": "order_action",
                  "params": {"action": "refund", "order_id": "ord_2002"}, "next": null}
               ]}
              """);
      assertThat(validator.validate(def).warnings())
          .extracting(Violation::code)
          .contains(ErrorCode.APPROVAL_NOT_GUARANTEED);
    }
  }

  @Test
  void shapeOnlyValidationAcceptsCatalogErrors() {
    ValidationReport r =
        validator.validateShape(
            wrap("{\"id\": \"n1\", \"type\": \"teleport\", \"params\": {}, \"next\": null}"));
    assertThat(r.isValid()).isTrue();
  }
}
