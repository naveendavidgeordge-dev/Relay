"use client";

import { AlertTriangle, Ban, HelpCircle, ShieldAlert, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { IdChip } from "@/components/CopyButton";
import { useAnnounce } from "@/components/Providers";
import { RelativeTime } from "@/components/RelativeTime";
import { RunStatusBadge } from "@/components/RunStatusBadge";
import { StepTimeline } from "@/components/StepTimeline";
import { TraceJsonViewer } from "@/components/TraceJsonViewer";
import { Button } from "@/components/ui/button";
import { Badge, Card, CardHeader } from "@/components/ui/primitives";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { WorkflowCanvas } from "@/components/WorkflowCanvas";
import { RelayApiError } from "@/lib/api/client";
import { browserApi } from "@/lib/api/browser";
import { TERMINAL, type Run } from "@/lib/api/types";
import { formatDuration, formatNumber } from "@/lib/utils";

/** Live run view: polls while the run is not terminal, announces status changes. */
export function RunDetail({ initial }: { initial: Run }) {
  const [run, setRun] = useState(initial);
  const announce = useAnnounce();
  const lastStatus = useRef(initial.status);

  useEffect(() => {
    if (TERMINAL.has(run.status)) return;
    const t = setInterval(async () => {
      try {
        const next = await browserApi.getRun(run.run_id);
        setRun(next);
      } catch {
        /* transient; keep polling */
      }
    }, 1500);
    return () => clearInterval(t);
  }, [run.run_id, run.status]);

  useEffect(() => {
    if (lastStatus.current !== run.status) {
      announce(`Run ${run.status.replace("_", " ")}`);
      if (TERMINAL.has(run.status)) {
        const fn =
          run.status === "succeeded"
            ? toast.success
            : run.status === "failed"
              ? toast.error
              : toast.message;
        fn(`Run ${run.status}`, { description: run.failure_reason ?? run.run_id });
      }
      lastStatus.current = run.status;
    }
  }, [run.status, run.failure_reason, run.run_id, announce]);

  const steps = run.steps ?? [];
  const visited = new Set(steps.map((s) => s.node_id));
  const pending = (run.approvals ?? []).filter((a) => a.status === "pending");

  return (
    <>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-xl font-semibold tracking-tight">
              {run.workflow_name ?? run.workflow_id}
            </h1>
            <RunStatusBadge status={run.status} />
            {run.cancel_requested && !TERMINAL.has(run.status) ? (
              <Badge tone="warn">cancel requested</Badge>
            ) : null}
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted">
            <IdChip id={run.run_id} />
            <Link href={`/workflows/${run.workflow_id}`} className="font-mono hover:text-accent">
              {run.workflow_id} · v{run.workflow_version}
            </Link>
            <span>trigger: {run.trigger_type}</span>
            <span>
              started <RelativeTime iso={run.started_at ?? run.created_at} />
            </span>
            <span className="font-mono">{formatDuration(run.duration_ms)}</span>
          </div>
        </div>
        {!TERMINAL.has(run.status) ? (
          <ConfirmDialog
            title="Cancel this run?"
            description={
              run.status === "running"
                ? "The step in flight finishes; no further nodes execute."
                : "The run ends cancelled and nothing else executes."
            }
            confirmLabel="Cancel run"
            confirmVariant="danger"
            onConfirm={async () => {
              try {
                setRun(await browserApi.cancelRun(run.run_id));
                toast.success("Cancellation requested");
              } catch (e) {
                toast.error("Cancel failed", {
                  description: e instanceof RelayApiError ? e.message : String(e),
                });
              }
            }}
            trigger={
              <Button variant="danger">
                <Ban aria-hidden /> Cancel run
              </Button>
            }
          />
        ) : null}
      </div>

      {run.error ? (
        <div
          role="alert"
          className={`mb-6 flex items-start gap-3 rounded-lg border p-4 text-sm ${
            run.status === "cancelled"
              ? "border-border bg-elevated text-muted"
              : "border-bad/40 bg-bad/5 text-bad"
          }`}
        >
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
          <div>
            <span className="font-mono text-2xs uppercase">{run.error.code}</span>
            <p className="mt-0.5">{run.error.message}</p>
          </div>
        </div>
      ) : null}

      {pending.length > 0 ? (
        <div className="mb-6 flex items-center justify-between gap-3 rounded-lg border border-warn/40 bg-warn/5 p-4 text-sm">
          <span>Paused for approval: {pending[0]?.message}</span>
          <Link
            href={`/approvals#${pending[0]?.id}`}
            className="shrink-0 text-xs text-warn underline"
          >
            Decide →
          </Link>
        </div>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
        <Tabs defaultValue="timeline">
          <TabsList aria-label="Run views">
            <TabsTrigger value="timeline">Timeline ({steps.length})</TabsTrigger>
            <TabsTrigger value="graph">Graph</TabsTrigger>
          </TabsList>
          <TabsContent value="timeline" className="mt-4">
            <StepTimeline steps={steps} />
          </TabsContent>
          <TabsContent value="graph" className="mt-4">
            {run.definition_snapshot ? (
              <WorkflowCanvas
                definition={run.definition_snapshot}
                visited={visited}
                current={run.current_node_id}
              />
            ) : null}
          </TabsContent>
        </Tabs>

        <aside className="space-y-4">
          <Card>
            <CardHeader
              title={
                <span className="inline-flex items-center gap-1.5">
                  <HelpCircle className="size-4 text-accent" aria-hidden /> Why did this run?
                </span>
              }
            />
            <div className="space-y-3 p-4 text-xs text-muted">
              <p>
                A <span className="text-fg">{run.trigger_type}</span> trigger started{" "}
                <span className="text-fg">{run.workflow_name ?? run.workflow_id}</span> (v
                {run.workflow_version}) with this payload as{" "}
                <span className="font-mono">{"{{trigger.body}}"}</span>:
              </p>
              <TraceJsonViewer label="Trigger payload" value={run.input ?? {}} expandDepth={2} />
            </div>
          </Card>
          <Card>
            <CardHeader title="Run" />
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 p-4 text-xs">
              <dt className="text-subtle">Steps</dt>
              <dd className="font-mono">
                {run.steps_executed}
                {run.max_steps ? ` / ${run.max_steps} (max_steps)` : ""}
              </dd>
              <dt className="text-subtle">AI tokens</dt>
              <dd className="font-mono">{formatNumber(run.ai_tokens_used)}</dd>
              <dt className="text-subtle">Current node</dt>
              <dd className="font-mono">{run.current_node_id ?? "—"}</dd>
              <dt className="text-subtle">Snapshot</dt>
              <dd>
                {run.signature_valid ? (
                  <span className="inline-flex items-center gap-1 text-ok">
                    <ShieldCheck className="size-3.5" aria-hidden /> ML-DSA-65 verified
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 text-bad">
                    <ShieldAlert className="size-3.5" aria-hidden /> signature invalid
                  </span>
                )}
              </dd>
              {run.trace_id ? (
                <>
                  <dt className="text-subtle">Trace</dt>
                  <dd className="truncate font-mono text-2xs">{run.trace_id}</dd>
                </>
              ) : null}
              {run.lease_owner ? (
                <>
                  <dt className="text-subtle">Worker</dt>
                  <dd className="truncate font-mono text-2xs">{run.lease_owner}</dd>
                </>
              ) : null}
              <dt className="text-subtle">Created</dt>
              <dd>
                <RelativeTime iso={run.created_at} />
              </dd>
              {run.finished_at ? (
                <>
                  <dt className="text-subtle">Finished</dt>
                  <dd>
                    <RelativeTime iso={run.finished_at} />
                  </dd>
                </>
              ) : null}
            </dl>
          </Card>
          {run.definition_snapshot ? (
            <TraceJsonViewer
              label="Definition snapshot"
              value={run.definition_snapshot}
              expandDepth={1}
            />
          ) : null}
        </aside>
      </div>
    </>
  );
}
