package com.relay.domain.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateSyntaxTest {

  @Test
  void parsesTriggerBodyPaths() {
    TemplateRef r = TemplateSyntax.parse("trigger.body.order_id").orElseThrow();
    assertThat(r.root()).isEqualTo(TemplateRef.Root.TRIGGER_BODY);
    assertThat(r.path()).containsExactly("order_id");
  }

  @Test
  void parsesWholeBody() {
    assertThat(TemplateSyntax.parse("trigger.body").orElseThrow().path()).isEmpty();
  }

  @Test
  void parsesNodeOutputPaths() {
    TemplateRef r = TemplateSyntax.parse("nodes.create_shipment.output.body.shipment_id").orElseThrow();
    assertThat(r.root()).isEqualTo(TemplateRef.Root.NODE_OUTPUT);
    assertThat(r.nodeId()).isEqualTo("create_shipment");
    assertThat(r.path()).containsExactly("body", "shipment_id");
  }

  @Test
  void rejectsAnythingThatIsNotAPathLookup() {
    for (String bad :
        List.of(
            "",
            "trigger",
            "trigger.headers.x",
            "nodes.a",
            "nodes.a.input.x",
            "env.HOME",
            "trigger.body.amount * 2",
            "trigger.body.a | upper",
            "trigger.body..a")) {
      assertThat(TemplateSyntax.parse(bad)).as(bad).isEmpty();
    }
  }

  @Test
  void extractsEveryExpressionIncludingDollarPrefixed() {
    assertThat(
            TemplateSyntax.expressions(
                "Expense of ${{trigger.body.amount_usd}} from {{ trigger.body.employee_email }}"))
        .containsExactly("trigger.body.amount_usd", "trigger.body.employee_email");
  }

  @Test
  void flagsUnclosedBraces() {
    assertThat(TemplateSyntax.expressions("hello {{trigger.body.name"))
        .containsExactly(TemplateSyntax.UNBALANCED);
  }

  @Test
  void detectsWholeStringTemplates() {
    assertThat(TemplateSyntax.isWholeTemplate("{{trigger.body.amount_usd}}")).isTrue();
    assertThat(TemplateSyntax.isWholeTemplate(" {{ trigger.body.amount_usd }} ")).isTrue();
    assertThat(TemplateSyntax.isWholeTemplate("$ {{trigger.body.amount_usd}}")).isFalse();
    assertThat(TemplateSyntax.isWholeTemplate("{{a}} {{b}}")).isFalse();
  }
}
