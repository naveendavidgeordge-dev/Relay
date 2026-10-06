import { AlertTriangle, Webhook } from "lucide-react";
import { notFound } from "next/navigation";
import { IdChip } from "@/components/CopyButton";
import { RelativeTime } from "@/components/RelativeTime";
import { ErrorState, PageHeader } from "@/components/States";
import { TraceJsonViewer } from "@/components/TraceJsonViewer";
import { Badge, Card, CardHeader } from "@/components/ui/primitives";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { WorkflowCanvas } from "@/components/WorkflowCanvas";
import { RelayApiError } from "@/lib/api/client";
import { serverApi } from "@/lib/api/server";
import { PublishButton, TriggerButton } from "./actions";

export const dynamic = "force-dynamic";

export default async function WorkflowDetailPage({ params }: { params: { id: string } }) {
  const api = serverApi();
  let workflow;
  let samples;
  try {
    [workflow, samples] = await Promise.all([
      api.getWorkflow(params.id),
      api.listSamples(params.id),
    ]);
  } catch (e) {
    if (e instanceof RelayApiError && e.status === 404) notFound();
    return <ErrorState error={e} />;
  }
  const def = workflow.published_definition ?? workflow.definition;
  const canPublish = workflow.status === "draft" || workflow.has_unpublished_changes;
  const validation = workflow.validation;

  return (
    <>
      <PageHeader
        title={workflow.name}
        description={workflow.description ?? undefined}
        actions={
          <>
            {canPublish ? (
              <PublishButton id={workflow.id} valid={validation?.valid ?? true} />
            ) : null}
            {workflow.status === "published" ? (
              <TriggerButton id={workflow.id} samples={samples} />
            ) : null}
          </>
        }
      />
      <div className="mb-6 flex flex-wrap items-center gap-3 text-xs text-muted">
        <IdChip id={workflow.id} />
        <Badge tone={workflow.status === "published" ? "ok" : "neutral"}>{workflow.status}</Badge>
        {workflow.published_version ? (
          <Badge tone="accent">v{workflow.published_version}</Badge>
        ) : null}
        {workflow.has_unpublished_changes && workflow.status === "published" ? (
          <Badge tone="warn">unpublished edits — runs use v{workflow.published_version}</Badge>
        ) : null}
        <span>trigger: {workflow.trigger_type}</span>
        {workflow.webhook_path ? (
          <span className="inline-flex items-center gap-1 font-mono">
            <Webhook className="size-3" aria-hidden /> POST {workflow.webhook_path}
          </span>
        ) : null}
        <span>
          updated <RelativeTime iso={workflow.updated_at} />
        </span>
      </div>

      {validation && (validation.errors.length > 0 || validation.warnings.length > 0) ? (
        <Card className={validation.errors.length ? "mb-6 border-bad/40" : "mb-6 border-warn/40"}>
          <ul className="space-y-1.5 p-4 text-sm">
            {[...validation.errors, ...validation.warnings].map((v, i) => (
              <li key={i} className="flex items-start gap-2">
                <AlertTriangle
                  className={`mt-0.5 size-4 shrink-0 ${i < validation.errors.length ? "text-bad" : "text-warn"}`}
                  aria-hidden
                />
                <span>
                  <span className="font-mono text-2xs text-subtle">{v.code}</span> {v.message}
                </span>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-[1fr_320px]">
        <Tabs defaultValue="graph">
          <TabsList aria-label="Workflow views">
            <TabsTrigger value="graph">Graph</TabsTrigger>
            <TabsTrigger value="definition">Definition</TabsTrigger>
            {workflow.has_unpublished_changes && workflow.published_definition ? (
              <TabsTrigger value="draft">Working copy</TabsTrigger>
            ) : null}
          </TabsList>
          <TabsContent value="graph" className="mt-4">
            {def ? <WorkflowCanvas definition={def} /> : null}
          </TabsContent>
          <TabsContent value="definition" className="mt-4">
            <TraceJsonViewer
              label={
                workflow.published_definition ? `Published v${workflow.published_version}` : "Draft"
              }
              value={def}
              expandDepth={3}
            />
          </TabsContent>
          <TabsContent value="draft" className="mt-4">
            <TraceJsonViewer
              label="Working copy (unpublished)"
              value={workflow.definition}
              expandDepth={3}
            />
          </TabsContent>
        </Tabs>
        <Card>
          <CardHeader
            title="Versions"
            description="Each publish freezes an ML-DSA-65 signed version"
          />
          <ul className="divide-y divide-border">
            {(workflow.versions ?? []).map((v) => (
              <li key={v.version} className="flex items-center justify-between px-6 py-3 text-xs">
                <span>
                  <span className="font-mono font-medium">v{v.version}</span>{" "}
                  <span className="text-muted">by {v.published_by}</span>
                </span>
                <span className="flex items-center gap-2">
                  {v.signature_valid ? (
                    <Badge tone="ok">signed</Badge>
                  ) : (
                    <Badge tone="bad">unverified</Badge>
                  )}
                  <RelativeTime iso={v.created_at} className="text-subtle" />
                </span>
              </li>
            ))}
            {(workflow.versions ?? []).length === 0 ? (
              <li className="px-6 py-4 text-xs text-muted">Not published yet.</li>
            ) : null}
          </ul>
        </Card>
      </div>
    </>
  );
}
