import { Ban, CheckCircle2, Clock, Loader2, PauseCircle, XCircle } from "lucide-react";
import type { RunStatus } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const STYLE: Record<RunStatus, { label: string; cls: string; Icon: typeof Clock }> = {
  queued: { label: "Queued", cls: "bg-elevated text-muted border-border", Icon: Clock },
  running: { label: "Running", cls: "bg-info/10 text-info border-info/30", Icon: Loader2 },
  waiting_approval: {
    label: "Waiting approval",
    cls: "bg-warn/10 text-warn border-warn/30",
    Icon: PauseCircle,
  },
  succeeded: { label: "Succeeded", cls: "bg-ok/10 text-ok border-ok/30", Icon: CheckCircle2 },
  failed: { label: "Failed", cls: "bg-bad/10 text-bad border-bad/30", Icon: XCircle },
  cancelled: { label: "Cancelled", cls: "bg-elevated text-subtle border-border", Icon: Ban },
};

/** Colored pill for the six contract run statuses. */
export function RunStatusBadge({ status, className }: { status: RunStatus; className?: string }) {
  const s = STYLE[status];
  return (
    <span
      data-status={status}
      aria-label={`Run status: ${s.label}`}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-2xs font-medium",
        s.cls,
        className,
      )}
    >
      <s.Icon aria-hidden className={cn("size-3.5", status === "running" && "animate-spin")} />
      {s.label}
    </span>
  );
}

/** Step statuses reuse the run palette where they overlap. */
export function StepStatusBadge({ status }: { status: string }) {
  const map: Record<string, RunStatus> = {
    succeeded: "succeeded",
    failed: "failed",
    running: "running",
    waiting: "waiting_approval",
    cancelled: "cancelled",
  };
  const s = map[status];
  if (!s) return <span className="text-2xs text-muted">{status}</span>;
  const label = status === "waiting" ? "Waiting" : STYLE[s].label;
  const { cls, Icon } = STYLE[s];
  return (
    <span
      data-step-status={status}
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-2xs font-medium",
        cls,
      )}
    >
      <Icon aria-hidden className={cn("size-3", status === "running" && "animate-spin")} />
      {label}
    </span>
  );
}
