"use client";

import { Ban, Rocket, Wand2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/States";
import { TraceJsonViewer } from "@/components/TraceJsonViewer";
import { Button } from "@/components/ui/button";
import { Badge, Card, CardHeader } from "@/components/ui/primitives";
import { WorkflowCanvas } from "@/components/WorkflowCanvas";
import { RelayApiError } from "@/lib/api/client";
import { browserApi } from "@/lib/api/browser";
import type { CompileResult } from "@/lib/api/types";

const EXAMPLES = [
  {
    id: "nl_demo_001",
    text: "When a webhook receives a new order, check if the total amount is over 100 dollars. If it is, ask a manager for approval and then email the customer a confirmation. Otherwise email the confirmation right away.",
  },
  {
    id: "nl_trap_001",
    text: "Every day at 9am, scan our Postgres database for new signups and update each one's record in Salesforce with a lead score.",
  },
  {
    id: "nl_011",
    text: "When an order webhook arrives, immediately refund the order in full without any human involvement, then email the customer.",
  },
];

/** Good To Have: plain English → validated draft, or a refusal naming missing capabilities. */
export default function CompilerPage() {
  const router = useRouter();
  const [text, setText] = useState(EXAMPLES[0]!.text);
  const [result, setResult] = useState<CompileResult | null>(null);
  const [busy, setBusy] = useState(false);

  async function compile() {
    setBusy(true);
    setResult(null);
    try {
      setResult(await browserApi.compile(text));
    } catch (e) {
      toast.error("Compile failed", {
        description: e instanceof RelayApiError ? e.message : String(e),
      });
    } finally {
      setBusy(false);
    }
  }

  async function publish() {
    // The draft was stored server-side by /workflows/compile (its webhook secret never reaches the browser).
    const id = result?.workflow_id;
    if (!id) return;
    try {
      await browserApi.publishWorkflow(id);
      toast.success("Published", { description: id });
      router.push(`/workflows/${id}`);
    } catch (e) {
      toast.error("Publish failed", {
        description: e instanceof RelayApiError ? `${e.code}: ${e.message}` : String(e),
      });
    }
  }

  return (
    <>
      <PageHeader
        title="NL compiler"
        description="Describe a workflow in English. The compiler may only use catalog node types; drafts must pass publish validation, and requests the catalog cannot express are refused."
      />
      <Card className="p-4">
        <label htmlFor="nl" className="text-xs text-muted">
          Description
        </label>
        <textarea
          id="nl"
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={4}
          className="mt-1 w-full rounded-md border border-border bg-bg p-3 text-sm"
        />
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Button variant="primary" onClick={compile} disabled={busy || text.trim().length < 10}>
            <Wand2 aria-hidden /> {busy ? "Compiling…" : "Compile"}
          </Button>
          <span className="text-2xs text-subtle">Examples:</span>
          {EXAMPLES.map((ex) => (
            <button
              key={ex.id}
              type="button"
              onClick={() => setText(ex.text)}
              className="rounded-md border border-border px-2 py-1 font-mono text-2xs text-muted hover:text-fg"
            >
              {ex.id}
            </button>
          ))}
        </div>
      </Card>

      {result?.refusal ? (
        <Card className="mt-6 border-warn/40">
          <CardHeader
            title={
              <span className="inline-flex items-center gap-2">
                <Ban className="size-4 text-warn" aria-hidden /> Refused — the catalog cannot
                express this
              </span>
            }
          />
          <div className="space-y-3 p-6 text-sm">
            <p>{result.refusal.reason}</p>
            {result.refusal.missing_capabilities?.length ? (
              <div className="flex flex-wrap gap-2">
                {result.refusal.missing_capabilities.map((c) => (
                  <Badge key={c} tone="warn">
                    missing: {c}
                  </Badge>
                ))}
              </div>
            ) : null}
          </div>
        </Card>
      ) : null}

      {result?.workflow ? (
        <div className="mt-6 grid gap-6 xl:grid-cols-2">
          <Card>
            <CardHeader
              title="Generated draft"
              description={
                result.validation?.valid
                  ? "Passes publish validation"
                  : "Validation problems — see below"
              }
              actions={
                <Button
                  variant="primary"
                  size="sm"
                  onClick={publish}
                  disabled={!result.validation?.valid}
                >
                  <Rocket aria-hidden /> Publish
                </Button>
              }
            />
            <div className="space-y-3 p-4">
              {result.explanation ? (
                <p className="text-sm text-muted">{result.explanation}</p>
              ) : null}
              {(result.notes ?? []).map((n) => (
                <p
                  key={n}
                  className="rounded-md border border-warn/30 bg-warn/5 p-2 text-xs text-warn"
                >
                  {n}
                </p>
              ))}
              {(result.validation?.warnings ?? []).map((w, i) => (
                <p key={i} className="text-2xs text-warn">
                  {w.code}: {w.message}
                </p>
              ))}
              <WorkflowCanvas definition={result.workflow} />
            </div>
          </Card>
          <TraceJsonViewer label="Definition" value={result.workflow} expandDepth={3} />
        </div>
      ) : null}
    </>
  );
}
