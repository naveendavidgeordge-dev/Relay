"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { ApprovalCard, type Decision } from "@/components/ApprovalCard";
import { useAnnounce } from "@/components/Providers";
import { EmptyState } from "@/components/States";
import { RelayApiError } from "@/lib/api/client";
import { browserApi } from "@/lib/api/browser";
import type { Approval } from "@/lib/api/types";

/** Pending approvals as cards with optimistic decisions (rolled back on error), history below. */
export function ApprovalsBoard({ initial }: { initial: Approval[] }) {
  const router = useRouter();
  const announce = useAnnounce();
  const [items, setItems] = useState(initial);
  const pending = items.filter((a) => a.status === "pending");
  const decided = items.filter((a) => a.status !== "pending");

  async function decide(a: Approval, decision: Decision, comment: string) {
    const before = items;
    const optimistic: Approval = {
      ...a,
      status: decision === "approve" ? "approved" : "rejected",
      decided_by: "you",
      decided_at: new Date().toISOString(),
      comment: comment || null,
    };
    setItems((xs) => [optimistic, ...xs.filter((x) => x.id !== a.id)]);
    try {
      const saved =
        decision === "approve"
          ? await browserApi.approve(a.id, comment || undefined)
          : await browserApi.reject(a.id, comment || undefined);
      setItems((xs) => [saved, ...xs.filter((x) => x.id !== a.id)]);
      toast.success(
        decision === "approve" ? "Approved — run resumed" : "Rejected — run cancelled",
        {
          description: a.run_id,
        },
      );
      announce(`Approval ${saved.status}`);
      router.refresh();
    } catch (e) {
      setItems(before);
      toast.error("Decision not recorded", {
        description: e instanceof RelayApiError ? `${e.code}: ${e.message}` : String(e),
      });
    }
  }

  return (
    <div className="space-y-10">
      <section aria-labelledby="pending-heading">
        <h2 id="pending-heading" className="mb-3 text-sm font-semibold">
          Pending <span className="text-subtle">({pending.length})</span>
        </h2>
        {pending.length === 0 ? (
          <div className="card">
            <EmptyState
              title="Nothing waiting for you"
              description="Approvals appear here when a run reaches an approval node — try wf_expense_approval with pay_101."
              action={{ href: "/workflows/wf_expense_approval", label: "Open wf_expense_approval" }}
            />
          </div>
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {pending.map((a) => (
              <ApprovalCard key={a.id} approval={a} onDecide={(d, c) => decide(a, d, c)} />
            ))}
          </div>
        )}
      </section>
      <section aria-labelledby="history-heading">
        <h2 id="history-heading" className="mb-3 text-sm font-semibold">
          Decided <span className="text-subtle">({decided.length})</span>
        </h2>
        {decided.length === 0 ? (
          <p className="text-xs text-muted">No decisions yet.</p>
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {decided.slice(0, 30).map((a) => (
              <ApprovalCard key={a.id} approval={a} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
