package com.relay.nodes;

import static com.relay.testing.Contexts.ctx;
import static com.relay.testing.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relay.domain.error.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Satisfies: "greater_than/less_than compare numerically when both sides parse as numbers;
 * otherwise the comparison fails the step with a clear error." — node_catalog.json, condition
 */
class ConditionNodeExecutorTest {

  private final ConditionNodeExecutor executor = new ConditionNodeExecutor();

  private boolean eval(String left, String op, String right) {
    var params =
        json("{\"left\": \"%s\", \"op\": \"%s\", \"right\": \"%s\"}".formatted(left, op, right));
    NodeResult.Completed c = (NodeResult.Completed) executor.execute(ctx("condition", "c", params, null));
    return c.output().path("result").asBoolean();
  }

  @ParameterizedTest(name = "{0} {1} {2} -> {3}")
  @CsvSource({
    // wf_expense_approval.is_large: pay_101 (250) pauses, pay_102 (40) auto-approves
    "250, greater_than, 100, true",
    "40, greater_than, 100, false",
    // numeric, not lexicographic ("9" > "100" as strings)
    "9, greater_than, 100, false",
    "9, less_than, 100, true",
    "100.00, greater_than, 100, false",
    "-5, less_than, 0, true",
    // wf_support_triage.route on the AI category
    "refund_request, equals, refund_request, true",
    "complaint, equals, refund_request, false",
    "complaint, not_equals, refund_request, true",
    // wf_runaway.is_shipped
    "processing, equals, shipped, false",
    "order shipped today, contains, shipped, true"
  })
  void comparisons(String left, String op, String right, boolean expected) {
    assertThat(eval(left, op, right)).isEqualTo(expected);
  }

  @Test
  void nonNumericComparisonFailsTheStepClearly() {
    var params = json("{\"left\": \"lots\", \"op\": \"greater_than\", \"right\": \"100\"}");
    assertThatThrownBy(() -> executor.execute(ctx("condition", "is_large", params, null)))
        .isInstanceOf(NodeFailureException.class)
        .hasMessageContaining("is_large")
        .hasMessageContaining("left='lots'")
        .extracting(e -> ((NodeFailureException) e).code())
        .isEqualTo(ErrorCode.CONDITION_NOT_NUMERIC);
  }

  @Test
  void outputIsExactlyTheCatalogShape() {
    var params = json("{\"left\": \"1\", \"op\": \"equals\", \"right\": \"1\"}");
    NodeResult.Completed c = (NodeResult.Completed) executor.execute(ctx("condition", "c", params, null));
    assertThat(c.output().properties()).hasSize(1);
    assertThat(c.output().get("result").isBoolean()).isTrue();
    assertThat(c.details()).singleElement().satisfies(d -> assertThat(d.get("branch").asText()).isEqualTo("on_true"));
  }
}
