"use client";

import { AnimatePresence, motion } from "framer-motion";
import { ChevronDown, KeyRound, RotateCcw, ShieldCheck, Sparkles } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import type { Step } from "@/lib/api/types";
import { cn, formatDuration } from "@/lib/utils";
import { CopyButton } from "./CopyButton";
import { NodeTypeBadge } from "./NodeTypeBadge";
import { RelativeTime } from "./RelativeTime";
import { StepStatusBadge } from "./RunStatusBadge";
import { TraceJsonViewer } from "./TraceJsonViewer";

/**
 * Which step opens by default: the first failed one; otherwise the first AI step (so the demo
 * shows tokens); otherwise none.
 */
export function defaultOpenSequence(steps: Step[]): number | null {
  const failed = steps.find((s) => s.status === "failed");
  if (failed) return failed.sequence;
  const ai = steps.find((s) => s.type === "ai");
  return ai ? ai.sequence : null;
}

const DOT: Record<string, string> = {
  succeeded: "bg-ok",
  failed: "bg-bad",
  running: "bg-info animate-pulse",
  waiting: "bg-warn",
  cancelled: "bg-subtle",
};

/** Vertical timeline of a run's steps with expandable input/output/error/attempt JSON. */
export function StepTimeline({ steps }: { steps: Step[] }) {
  const initial = useMemo(() => defaultOpenSequence(steps), [steps]);
  const [open, setOpen] = useState<Set<number>>(() => new Set(initial === null ? [] : [initial]));

  if (steps.length === 0) {
    return (
      <p className="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted">
        No steps yet — the run is queued for a worker.
      </p>
    );
  }

  const toggle = (seq: number) =>
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(seq)) next.delete(seq);
      else next.add(seq);
      return next;
    });

  return (
    <ol className="relative" aria-label="Run steps">
      {steps.map((s, i) => {
        const isOpen = open.has(s.sequence);
        const tokens =
          s.tokens_prompt !== null && s.tokens_prompt !== undefined
            ? (s.tokens_prompt ?? 0) + (s.tokens_completion ?? 0)
            : null;
        return (
          <motion.li
            key={s.sequence}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.15, delay: Math.min(i, 10) * 0.02 }}
            className="relative pb-3 pl-8"
            data-testid={`step-${s.sequence}`}
          >
            {i < steps.length - 1 ? (
              <span
                aria-hidden
                className="absolute left-[11px] top-6 h-[calc(100%-12px)] w-px bg-border"
              />
            ) : null}
            <span
              aria-hidden
              className={cn(
                "absolute left-1.5 top-[18px] size-3 rounded-full ring-4 ring-bg",
                DOT[s.status] ?? "bg-subtle",
              )}
            />
            <div className={cn("card overflow-hidden", s.status === "failed" && "border-bad/40")}>
              <button
                type="button"
                aria-expanded={isOpen}
                aria-controls={`step-body-${s.sequence}`}
                onClick={() => toggle(s.sequence)}
                className="flex w-full flex-wrap items-center gap-x-3 gap-y-2 px-4 py-3 text-left hover:bg-elevated/50"
              >
                <span className="w-6 font-mono text-2xs text-subtle">#{s.sequence}</span>
                <NodeTypeBadge type={s.type} requiresApproval={s.requires_approval} />
                <span className="font-mono text-sm font-medium">{s.node_id}</span>
                <StepStatusBadge status={s.status} />
                {s.attempt > 1 ? (
                  <span
                    className="inline-flex items-center gap-1 text-2xs text-warn"
                    title="attempts"
                  >
                    <RotateCcw className="size-3" aria-hidden />
                    {s.attempt} attempts
                  </span>
                ) : null}
                {tokens !== null ? (
                  <span className="inline-flex items-center gap-1 rounded-full bg-accent/10 px-2 py-0.5 text-2xs text-accent">
                    <Sparkles className="size-3" aria-hidden />
                    {s.tokens_prompt} + {s.tokens_completion} tokens
                  </span>
                ) : null}
                <span className="ml-auto flex items-center gap-3 text-2xs text-subtle">
                  <RelativeTime iso={s.started_at} />
                  <span className="font-mono">{formatDuration(s.duration_ms)}</span>
                  <ChevronDown
                    aria-hidden
                    className={cn("size-4 transition-transform", isOpen && "rotate-180")}
                  />
                </span>
              </button>
              {s.idempotency_key || s.approval_id ? (
                <div className="flex flex-wrap gap-2 px-4 pb-3 pl-[52px]">
                  {s.idempotency_key ? (
                    <span
                      className="inline-flex items-center gap-1 rounded border border-border bg-bg px-1.5 py-0.5 font-mono text-2xs text-muted"
                      data-testid="idempotency-chip"
                    >
                      <KeyRound className="size-3" aria-hidden />
                      idempotency key: {s.idempotency_key}
                    </span>
                  ) : null}
                  {s.approval_id && s.requires_approval ? (
                    <Link
                      href={`/approvals#${s.approval_id}`}
                      className="inline-flex items-center gap-1 rounded border border-warn/30 bg-warn/10 px-1.5 py-0.5 font-mono text-2xs text-warn hover:underline"
                    >
                      <ShieldCheck className="size-3" aria-hidden />
                      gated by {s.approval_id}
                    </Link>
                  ) : null}
                </div>
              ) : null}
              <AnimatePresence initial={false}>
                {isOpen ? (
                  <motion.div
                    id={`step-body-${s.sequence}`}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.12 }}
                    className="grid gap-3 border-t border-border p-4 lg:grid-cols-2"
                  >
                    {s.error ? (
                      <div
                        className="rounded-md border border-bad/30 bg-bad/5 p-3 text-sm text-bad lg:col-span-2"
                        role="alert"
                      >
                        <span className="font-mono text-2xs uppercase">{s.error.code}</span>
                        <p className="mt-1">{s.error.message}</p>
                      </div>
                    ) : null}
                    <TraceJsonViewer label="Resolved input" value={s.input ?? null} />
                    <TraceJsonViewer label="Output" value={s.output ?? null} />
                    {s.attempts && s.attempts.length > 0 ? (
                      <TraceJsonViewer
                        label="Attempts"
                        value={s.attempts}
                        className="lg:col-span-2"
                        expandDepth={1}
                      />
                    ) : null}
                    <div className="flex items-center gap-1 text-2xs text-subtle lg:col-span-2">
                      node <span className="font-mono">{s.node_id}</span>
                      <CopyButton value={s.node_id} label="Copy node id" />
                    </div>
                  </motion.div>
                ) : null}
              </AnimatePresence>
            </div>
          </motion.li>
        );
      })}
    </ol>
  );
}
