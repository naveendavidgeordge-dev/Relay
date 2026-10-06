import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { defaultOpenSequence, StepTimeline } from "@/components/StepTimeline";
import { step } from "./fixtures";

const triage = [
  step({
    sequence: 1,
    node_id: "classify",
    type: "ai",
    status: "succeeded",
    tokens_prompt: 120,
    tokens_completion: 30,
    input: { prompt: "Classify…" },
    output: { category: "refund_request" },
  }),
  step({
    sequence: 2,
    node_id: "route",
    type: "condition",
    status: "succeeded",
    output: { result: true },
  }),
  step({
    sequence: 3,
    node_id: "refund_gate",
    type: "approval",
    status: "succeeded",
    approval_id: "apr_1",
  }),
  step({
    sequence: 4,
    node_id: "issue_refund",
    type: "order_action",
    status: "succeeded",
    side_effect: true,
    requires_approval: true,
    idempotency_key: "run_x:issue_refund",
    approval_id: "apr_1",
  }),
];

describe("StepTimeline", () => {
  it("opens the first AI step by default when nothing failed", () => {
    expect(defaultOpenSequence(triage)).toBe(1);
    render(<StepTimeline steps={triage} />);
    const first = screen.getByTestId("step-1");
    expect(first.querySelector('button[aria-controls="step-body-1"]')).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    expect(within(first).getByText("Resolved input")).toBeInTheDocument();
  });

  it("opens the first failed step when there is one", () => {
    const steps = [
      ...triage.slice(0, 2),
      step({
        sequence: 3,
        node_id: "x",
        type: "notify",
        status: "failed",
        error: { code: "upstream_rejected", message: "boom" },
      }),
    ];
    expect(defaultOpenSequence(steps)).toBe(3);
    render(<StepTimeline steps={steps} />);
    expect(screen.getByRole("alert")).toHaveTextContent("boom");
  });

  it("shows tokens for AI steps", () => {
    render(<StepTimeline steps={triage} />);
    expect(screen.getByText("120 + 30 tokens")).toBeInTheDocument();
  });

  it("shows the idempotency key chip and the approval gate on side-effect steps", () => {
    render(<StepTimeline steps={triage} />);
    expect(screen.getByTestId("idempotency-chip")).toHaveTextContent(
      "idempotency key: run_x:issue_refund",
    );
    expect(screen.getByRole("link", { name: /gated by apr_1/ })).toHaveAttribute(
      "href",
      "/approvals#apr_1",
    );
  });

  it("shows retry counts", () => {
    render(
      <StepTimeline
        steps={[
          step({ sequence: 1, node_id: "n", type: "notify", status: "succeeded", attempt: 3 }),
        ]}
      />,
    );
    expect(screen.getByText("3 attempts")).toBeInTheDocument();
  });

  it("toggles a step open and closed", async () => {
    render(<StepTimeline steps={triage} />);
    const route = screen
      .getByTestId("step-2")
      .querySelector('button[aria-controls="step-body-2"]')!;
    expect(route).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(route);
    expect(route).toHaveAttribute("aria-expanded", "true");
  });

  it("renders an empty state for queued runs", () => {
    render(<StepTimeline steps={[]} />);
    expect(screen.getByText(/queued for a worker/)).toBeInTheDocument();
  });
});
