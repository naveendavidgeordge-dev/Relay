"use client";

import { AlertTriangle, CheckCircle2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/States";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/primitives";
import { WorkflowCanvas } from "@/components/WorkflowCanvas";
import { RelayApiError } from "@/lib/api/client";
import { browserApi } from "@/lib/api/browser";
import { DefinitionSchema, type Validation } from "@/lib/api/types";

const TEMPLATE = {
  id: "wf_hello",
  name: "Hello world",
  trigger: { type: "manual" },
  entry: "hello",
  limits: { max_steps: 5, timeout_seconds: 60, max_ai_tokens: 0 },
  nodes: [
    {
      id: "hello",
      type: "notify",
      params: { channel: "chat", to: "#general", message: "Hello from {{trigger.body.name}}" },
      next: null,
    },
  ],
};

/** Paste-JSON editor with live publish validation (POST /workflows/validate, debounced). */
export default function NewWorkflowPage() {
  const router = useRouter();
  const [text, setText] = useState(() => JSON.stringify(TEMPLATE, null, 2));
  const [validation, setValidation] = useState<Validation | null>(null);
  const [parseError, setParseError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  let parsed: unknown = null;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = null;
  }
  const shape = DefinitionSchema.safeParse(parsed);

  useEffect(() => {
    let cancelled = false;
    const t = setTimeout(async () => {
      try {
        const v: unknown = JSON.parse(text);
        setParseError(null);
        const res = await browserApi.validateWorkflow(v);
        if (!cancelled) setValidation(res);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof SyntaxError) {
          setParseError(e.message);
          setValidation(null);
        }
      }
    }, 350);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [text]);

  async function save(publish: boolean) {
    setBusy(true);
    try {
      const created = await browserApi.createWorkflow(JSON.parse(text));
      if (publish) await browserApi.publishWorkflow(created.id);
      toast.success(publish ? "Created and published" : "Draft created", {
        description: created.id,
      });
      router.push(`/workflows/${created.id}`);
    } catch (e) {
      toast.error("Not saved", {
        description: e instanceof RelayApiError ? `${e.code}: ${e.message}` : String(e),
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        title="New workflow"
        description="Paste a definition in the seed shape. It is validated against the node catalog as you type."
        actions={
          <>
            <Button disabled={busy || !!parseError} onClick={() => save(false)}>
              Save draft
            </Button>
            <Button
              variant="primary"
              disabled={busy || !validation?.valid}
              onClick={() => save(true)}
            >
              Save &amp; publish
            </Button>
          </>
        }
      />
      <div className="grid gap-6 xl:grid-cols-2">
        <label className="block">
          <span className="sr-only">Workflow definition JSON</span>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            spellCheck={false}
            rows={28}
            className="card w-full resize-y bg-bg p-4 font-mono text-xs leading-5"
          />
        </label>
        <div className="space-y-4">
          <Card className="p-4" aria-live="polite">
            {parseError ? (
              <p className="flex items-start gap-2 text-sm text-bad">
                <AlertTriangle className="mt-0.5 size-4" aria-hidden /> JSON: {parseError}
              </p>
            ) : validation === null ? (
              <p className="text-sm text-muted">Validating…</p>
            ) : (
              <>
                <p
                  className={`flex items-center gap-2 text-sm font-medium ${validation.valid ? "text-ok" : "text-bad"}`}
                >
                  {validation.valid ? (
                    <CheckCircle2 className="size-4" aria-hidden />
                  ) : (
                    <AlertTriangle className="size-4" aria-hidden />
                  )}
                  {validation.valid
                    ? "Ready to publish"
                    : `${validation.errors.length} blocking problem(s)`}
                </p>
                <ul className="mt-3 space-y-1.5 text-xs">
                  {validation.errors.map((v, i) => (
                    <li key={`e${i}`} className="text-bad">
                      <span className="font-mono">{v.code}</span> — {v.message}
                    </li>
                  ))}
                  {validation.warnings.map((v, i) => (
                    <li key={`w${i}`} className="text-warn">
                      <span className="font-mono">{v.code}</span> — {v.message}
                    </li>
                  ))}
                </ul>
              </>
            )}
          </Card>
          {shape.success ? <WorkflowCanvas definition={shape.data} /> : null}
        </div>
      </div>
    </>
  );
}
