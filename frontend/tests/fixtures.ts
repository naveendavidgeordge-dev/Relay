import seeds from "../../data/seed_workflows.json";
import type { Approval, Definition, Step } from "@/lib/api/types";

/** The real seed definitions (read-only fixtures from /data). */
export function seed(id: string): Definition {
  const wf = (seeds.workflows as unknown as Definition[]).find((w) => w.id === id);
  if (!wf) throw new Error(`no seed ${id}`);
  return wf;
}

export function step(
  partial: Partial<Step> & Pick<Step, "sequence" | "node_id" | "type" | "status">,
): Step {
  return {
    attempt: 1,
    started_at: "2026-07-04T10:02:12Z",
    duration_ms: 3,
    side_effect: false,
    requires_approval: false,
    ...partial,
  };
}

export function approval(partial: Partial<Approval> = {}): Approval {
  return {
    id: "apr_7f2c",
    run_id: "run_01h9abc",
    workflow_id: "wf_expense_approval",
    node_id: "finance_gate",
    step_sequence: 2,
    message: "Expense of $250 from dev1@example.com: Conference ticket",
    status: "pending",
    created_at: "2026-07-04T10:02:12Z",
    ...partial,
  };
}
