package com.relay.application.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.domain.validation.PublishValidator;
import com.relay.testing.FakeModel;
import com.relay.testing.Fixtures;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Runs the full {@code data/nl_eval.jsonl} eval offline (heuristic strategy, nothing persisted)
 * and pins the expected outcomes, so a regression in the planner shows up as a test failure.
 *
 * <p>Satisfies: "Build a one-command eval over {@code data/nl_eval.jsonl} (12 buildable cases, 3
 * traps) that scores structural assertions and reports accuracy." — Case Study, Good To Have.
 */
class NlCompilerEvalTest {

  private final PublishValidator validator = new PublishValidator(Fixtures.catalog(), false);
  private final CompilerGuardrails guardrails = new CompilerGuardrails(Fixtures.catalog());
  private final ObjectMapper mapper = new ObjectMapper();
  private final CompilerService service =
      new CompilerService(
          new HeuristicWorkflowCompiler(),
          new LlmWorkflowCompiler(new FakeModel(), Fixtures.catalog(), validator, mapper),
          guardrails,
          validator,
          null,
          null,
          "heuristic");
  private final NlEvalService eval = new NlEvalService(service, guardrails, validator, mapper);

  @Test
  void evalReportsAccuracyOverAllFifteenCases() {
    NlEvalService.Report report = eval.run();
    Map<String, String> byId =
        report.cases().stream().collect(Collectors.toMap(NlEvalService.CaseResult::id, NlEvalService.CaseResult::result));
    System.out.println("NL eval: " + report.correct() + "/" + report.total() + " = " + report.accuracy());
    report.cases().forEach(c -> System.out.println("  " + c.id() + " " + c.result() + " " + c.checks()));

    assertThat(report.total()).isEqualTo(15);
    assertThat(report.compiler()).isEqualTo("heuristic");
    // traps are always refused, never compiled with invented nodes
    assertThat(byId).containsEntry("nl_trap_001", "pass").containsEntry("nl_trap_002", "pass").containsEntry("nl_trap_003", "pass");
    // the demo and the gated-action cases
    assertThat(byId).containsEntry("nl_demo_001", "pass").containsEntry("nl_004", "pass")
        .containsEntry("nl_009", "pass").containsEntry("nl_011", "pass");
    assertThat(report.accuracy()).isGreaterThanOrEqualTo(0.8);
  }

  @Test
  void noCaseEverEmitsATypeOutsideTheCatalog() {
    eval.run().cases().forEach(c -> assertThat(c.checks()).containsEntry("no_invented_types", true));
  }

  @Test
  void refusalNamesTheMissingCapabilities() {
    CompilerService.Result r =
        service.compile(
            "Every day at 9am, scan our Postgres database for new signups and update each one's record in Salesforce with a lead score.",
            false,
            "test");
    assertThat(r.refusal()).isNotNull();
    assertThat(r.refusal().missingCapabilities())
        .anySatisfy(m -> assertThat(m).contains("database"))
        .anySatisfy(m -> assertThat(m).contains("CRM"));
    assertThat(r.workflow()).isNull();
  }

  @Test
  void refundWithoutHumansGetsAnApprovalGateInsertedAndSaysSo() {
    CompilerService.Result r =
        service.compile(
            "When an order webhook arrives, immediately refund the order in full without any human involvement, then email the customer.",
            false,
            "test");
    JsonNode def = r.definition();
    assertThat(r.validation().valid()).isTrue();
    String entry = def.path("entry").asText();
    JsonNode first = null;
    for (JsonNode n : def.path("nodes")) {
      if (n.path("id").asText().equals(entry)) {
        first = n;
      }
    }
    assertThat(first).isNotNull();
    assertThat(first.path("type").asText()).isEqualTo("approval");
    assertThat(r.notes()).anySatisfy(n -> assertThat(n).contains("requires_approval"));
  }

  @Test
  void demoCaseBranchesWithApprovalOnOneSide() {
    CompilerService.Result r =
        service.compile(
            "When a webhook receives a new order, check if the total amount is over 100 dollars. If it is, ask a manager for approval and then email the customer a confirmation. Otherwise email the confirmation right away.",
            false,
            "test");
    JsonNode def = r.definition();
    assertThat(def.path("trigger").path("type").asText()).isEqualTo("webhook");
    assertThat(r.workflow().path("trigger").path("secret").asText()).as("secret redacted").isEqualTo("********");
    JsonNode cond = def.path("nodes").get(0);
    assertThat(cond.path("type").asText()).isEqualTo("condition");
    assertThat(cond.path("params").path("op").asText()).isEqualTo("greater_than");
    assertThat(cond.path("params").path("right").asText()).isEqualTo("100");
    assertThat(cond.path("on_true").asText()).isEqualTo("manager_approval");
    assertThat(cond.path("on_false").asText()).startsWith("email_");
  }
}
