"use client";

import { Play, Rocket } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogTrigger } from "@/components/ui/dialog";
import { RelayApiError } from "@/lib/api/client";
import { browserApi } from "@/lib/api/browser";
import type { Sample } from "@/lib/api/types";

function errorText(e: unknown): string {
  return e instanceof RelayApiError ? `${e.code}: ${e.message}` : String(e);
}

export function PublishButton({ id, valid }: { id: string; valid: boolean }) {
  const router = useRouter();
  return (
    <ConfirmDialog
      title="Publish this workflow?"
      description="The working copy is validated against the node catalog and frozen as a new signed version. Existing runs keep executing their own snapshot."
      confirmLabel="Publish"
      onConfirm={async () => {
        try {
          const w = await browserApi.publishWorkflow(id);
          toast.success(`Published v${w.published_version}`);
          router.refresh();
        } catch (e) {
          toast.error("Publish rejected", { description: errorText(e) });
        }
      }}
      trigger={
        <Button variant={valid ? "primary" : "secondary"}>
          <Rocket aria-hidden /> Publish
        </Button>
      }
    />
  );
}

/** Manual trigger with an optional sample payload from data/sample_payloads.jsonl. */
export function TriggerButton({ id, samples }: { id: string; samples: Sample[] }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [text, setText] = useState(() => JSON.stringify(samples[0]?.body ?? {}, null, 2));
  const [busy, setBusy] = useState(false);
  let parseError: string | null = null;
  try {
    const v: unknown = JSON.parse(text);
    if (typeof v !== "object" || v === null || Array.isArray(v))
      parseError = "Input must be a JSON object";
  } catch (e) {
    parseError = e instanceof Error ? e.message : "Invalid JSON";
  }
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="primary">
          <Play aria-hidden /> Trigger
        </Button>
      </DialogTrigger>
      <DialogContent
        title="Trigger a run"
        description="The body becomes {{trigger.body}}. The run is queued; a worker executes it."
      >
        {samples.length > 0 ? (
          <div className="mb-3 flex flex-wrap gap-1.5">
            {samples.map((s) => (
              <button
                key={s.id}
                type="button"
                title={s.note ?? undefined}
                onClick={() => setText(JSON.stringify(s.body, null, 2))}
                className="rounded-md border border-border bg-elevated px-2 py-1 font-mono text-2xs text-muted hover:text-fg"
              >
                {s.id}
              </button>
            ))}
          </div>
        ) : null}
        <label className="block text-xs text-muted">
          Input JSON
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={10}
            spellCheck={false}
            className="mt-1 w-full rounded-md border border-border bg-bg p-3 font-mono text-xs text-fg"
          />
        </label>
        {parseError ? <p className="mt-1 text-2xs text-bad">{parseError}</p> : null}
        <div className="mt-4 flex justify-end">
          <Button
            variant="primary"
            disabled={busy || parseError !== null}
            onClick={async () => {
              setBusy(true);
              try {
                const r = await browserApi.triggerWorkflow(
                  id,
                  JSON.parse(text) as Record<string, unknown>,
                );
                toast.success("Run queued", { description: r.run_id });
                setOpen(false);
                router.push(`/runs/${r.run_id}`);
              } catch (e) {
                toast.error("Trigger failed", { description: errorText(e) });
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? "Queuing…" : "Trigger run"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
