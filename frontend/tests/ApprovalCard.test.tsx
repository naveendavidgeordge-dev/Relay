import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApprovalCard } from "@/components/ApprovalCard";
import { approval } from "./fixtures";

describe("ApprovalCard", () => {
  it("shows the message, the run link and both decisions while pending", () => {
    render(<ApprovalCard approval={approval()} onDecide={vi.fn()} />);
    expect(
      screen.getByText("Expense of $250 from dev1@example.com: Conference ticket"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "run_01h9abc" })).toHaveAttribute(
      "href",
      "/runs/run_01h9abc",
    );
    expect(screen.getByRole("button", { name: /Approve/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Reject/ })).toBeInTheDocument();
  });

  it("asks for confirmation before approving, then calls onDecide with the comment", async () => {
    const onDecide = vi.fn().mockResolvedValue(undefined);
    render(<ApprovalCard approval={approval()} onDecide={onDecide} />);
    await userEvent.click(screen.getByRole("button", { name: /Approve/ }));
    expect(onDecide).not.toHaveBeenCalled();
    await userEvent.type(screen.getByLabelText(/Comment/), "looks fine");
    const confirm = screen.getAllByRole("button", { name: "Approve" }).at(-1)!;
    await userEvent.click(confirm);
    expect(onDecide).toHaveBeenCalledWith("approve", "looks fine");
  });

  it("reject goes through its own confirmation", async () => {
    const onDecide = vi.fn().mockResolvedValue(undefined);
    render(<ApprovalCard approval={approval()} onDecide={onDecide} />);
    await userEvent.click(screen.getByRole("button", { name: /Reject/ }));
    await userEvent.click(screen.getAllByRole("button", { name: "Reject" }).at(-1)!);
    expect(onDecide).toHaveBeenCalledWith("reject", "");
  });

  it("shows decider and hides the buttons once decided", () => {
    render(
      <ApprovalCard
        approval={approval({
          status: "approved",
          decided_by: "alice",
          decided_at: "2026-07-04T10:05:00Z",
          signature_valid: true,
        })}
        onDecide={vi.fn()}
      />,
    );
    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.getByText("signed")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Approve/ })).not.toBeInTheDocument();
  });
});
