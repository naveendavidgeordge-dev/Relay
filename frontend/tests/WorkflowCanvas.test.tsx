import { render, screen } from "@testing-library/react";
import { layoutDefinition, WorkflowCanvas } from "@/components/WorkflowCanvas";
import { seed } from "./fixtures";

describe("WorkflowCanvas", () => {
  it("lays out every node of the triage seed", () => {
    const L = layoutDefinition(seed("wf_support_triage"));
    expect(L.nodes.map((n) => n.id).sort()).toEqual(
      [
        "classify",
        "issue_refund",
        "notify_customer",
        "notify_support",
        "refund_gate",
        "route",
      ].sort(),
    );
    expect(L.width).toBeGreaterThan(0);
  });

  it("splits condition branches into true and false edges", () => {
    const L = layoutDefinition(seed("wf_expense_approval"));
    const fromCondition = L.edges.filter((e) => e.from === "is_large");
    expect(fromCondition.map((e) => [e.to, e.kind])).toEqual([
      ["finance_gate", "true"],
      ["auto_ok", "false"],
    ]);
  });

  it("marks loop edges as backward (wf_runaway hold -> check_order)", () => {
    const L = layoutDefinition(seed("wf_runaway"));
    const back = L.edges.filter((e) => e.back);
    expect(back.map((e) => `${e.from}->${e.to}`)).toEqual(["hold->check_order"]);
  });

  it("renders an accessible SVG with labelled branches", () => {
    const { container } = render(
      <WorkflowCanvas definition={seed("wf_expense_approval")} visited={new Set(["is_large"])} />,
    );
    expect(screen.getByRole("img", { name: /Graph of Expense approval/ })).toBeInTheDocument();
    expect(container.querySelector('[data-edge="is_large->finance_gate"]')).toHaveAttribute(
      "data-kind",
      "true",
    );
    expect(screen.getByText("true")).toBeInTheDocument();
    expect(screen.getByText("false")).toBeInTheDocument();
  });
});
