import { render, screen } from "@testing-library/react";
import { RunStatusBadge, StepStatusBadge } from "@/components/RunStatusBadge";
import { RUN_STATUSES } from "@/lib/api/types";

describe("RunStatusBadge", () => {
  it.each(RUN_STATUSES)("renders an accessible label for %s", (status) => {
    render(<RunStatusBadge status={status} />);
    const badge = screen.getByLabelText(/Run status:/);
    expect(badge).toHaveAttribute("data-status", status);
  });

  it("covers exactly the six contract statuses", () => {
    expect([...RUN_STATUSES]).toEqual([
      "queued",
      "running",
      "waiting_approval",
      "succeeded",
      "failed",
      "cancelled",
    ]);
  });

  it("uses readable labels", () => {
    render(<RunStatusBadge status="waiting_approval" />);
    expect(screen.getByText("Waiting approval")).toBeInTheDocument();
  });

  it("maps step statuses onto the run palette", () => {
    render(<StepStatusBadge status="waiting" />);
    expect(screen.getByText("Waiting")).toHaveAttribute("data-step-status", "waiting");
  });
});
