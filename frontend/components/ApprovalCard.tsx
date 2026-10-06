"use client";

import { Check, ShieldCheck, X } from "lucide-react";
import Link from "next/link";
import type { Approval } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { ConfirmDialog } from "./ConfirmDialog";
import { IdChip } from "./CopyButton";
import { RelativeTime } from "./RelativeTime";
import { Badge } from "./ui/primitives";
import { Button } from "./ui/button";

export type Decision = "approve" | "reject";

/**
 * One approval: the resolved message, its run, and — while pending — Approve / Reject with a
 * confirmation step. Decided approvals show who decided and when.
 */
export function ApprovalCard({
  approval,
  onDecide,
}: {
  approval: Approval;
  onDecide?: (decision: Decision, comment: string) => Promise<void>;
}) {
  const pending = approval.status === "pending";
  const tone =
    approval.status === "approved"
      ? "ok"
      : approval.status === "rejected"
        ? "bad"
        : pending
          ? "warn"
          : "neutral";
  return (
    <article
      id={approval.id}
      aria-labelledby={`${approval.id}-title`}
      className={cn("card scroll-mt-24 p-5", pending && "border-warn/40")}
    >
      <header className="flex flex-wrap items-center gap-2">
        <Badge tone={tone}>{approval.status}</Badge>
        <IdChip id={approval.id} />
        <span className="ml-auto text-2xs text-subtle">
          requested <RelativeTime iso={approval.created_at} />
        </span>
      </header>
      <p id={`${approval.id}-title`} className="mt-3 text-sm leading-6">
        {approval.message}
      </p>
      <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs text-muted">
        <dt>Run</dt>
        <dd>
          <Link href={`/runs/${approval.run_id}`} className="font-mono text-accent hover:underline">
            {approval.run_id}
          </Link>
        </dd>
        {approval.workflow_id ? (
          <>
            <dt>Workflow</dt>
            <dd className="font-mono">{approval.workflow_id}</dd>
          </>
        ) : null}
        <dt>Node</dt>
        <dd className="font-mono">{approval.node_id}</dd>
        {approval.decided_by ? (
          <>
            <dt>Decided by</dt>
            <dd>
              <span className="text-fg">{approval.decided_by}</span>{" "}
              {approval.decided_at ? <RelativeTime iso={approval.decided_at} /> : null}
              {approval.signature_valid ? (
                <span
                  className="ml-2 inline-flex items-center gap-1 text-ok"
                  title="ML-DSA-65 signature verified"
                >
                  <ShieldCheck className="size-3" aria-hidden /> signed
                </span>
              ) : null}
            </dd>
          </>
        ) : null}
        {approval.comment ? (
          <>
            <dt>Comment</dt>
            <dd>{approval.comment}</dd>
          </>
        ) : null}
      </dl>
      {pending && onDecide ? (
        <footer className="mt-4 flex gap-2">
          <ConfirmDialog
            title="Approve this request?"
            description="The run resumes immediately and any gated action executes."
            confirmLabel="Approve"
            confirmVariant="success"
            withComment
            onConfirm={(c) => onDecide("approve", c)}
            trigger={
              <Button variant="success" size="sm">
                <Check aria-hidden /> Approve
              </Button>
            }
          />
          <ConfirmDialog
            title="Reject this request?"
            description="The run ends cancelled and nothing after the approval executes."
            confirmLabel="Reject"
            confirmVariant="danger"
            withComment
            onConfirm={(c) => onDecide("reject", c)}
            trigger={
              <Button variant="danger" size="sm">
                <X aria-hidden /> Reject
              </Button>
            }
          />
        </footer>
      ) : null}
    </article>
  );
}
