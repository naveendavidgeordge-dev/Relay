import { RunsTable } from "@/components/RunsTable";
import { EmptyState, ErrorState, PageHeader } from "@/components/States";
import { Card } from "@/components/ui/primitives";
import { serverApi } from "@/lib/api/server";
import { RUN_STATUSES, type RunStatus } from "@/lib/api/types";
import { RunFilters } from "./filters";

export const dynamic = "force-dynamic";
export const metadata = { title: "Runs" };

type Search = { status?: string; workflow_id?: string; from?: string; to?: string };

export default async function RunsPage({ searchParams }: { searchParams: Search }) {
  const status = (RUN_STATUSES as readonly string[]).includes(searchParams.status ?? "")
    ? (searchParams.status as RunStatus)
    : "";
  const api = serverApi();
  let runs;
  let workflows;
  try {
    [runs, workflows] = await Promise.all([
      api.listRuns({
        status,
        workflowId: searchParams.workflow_id || undefined,
        from: searchParams.from ? `${searchParams.from}T00:00:00Z` : undefined,
        to: searchParams.to ? `${searchParams.to}T23:59:59Z` : undefined,
        limit: 200,
      }),
      api.listWorkflows(),
    ]);
  } catch (e) {
    return (
      <>
        <PageHeader title="Runs" />
        <ErrorState error={e} />
      </>
    );
  }
  const filtered = Boolean(
    status || searchParams.workflow_id || searchParams.from || searchParams.to,
  );
  return (
    <>
      <PageHeader
        title="Runs"
        description="Every run executes the definition snapshot taken when it was triggered."
      />
      <RunFilters
        workflows={workflows.map((w) => ({ id: w.id, name: w.name }))}
        current={{
          status,
          workflow_id: searchParams.workflow_id ?? "",
          from: searchParams.from ?? "",
          to: searchParams.to ?? "",
        }}
      />
      <Card className="mt-4">
        {runs.length === 0 ? (
          <EmptyState
            title={filtered ? "No runs match these filters" : "No runs yet"}
            description={
              filtered
                ? "Clear a filter or widen the date range."
                : "Trigger a workflow to see its trace here."
            }
            action={
              filtered
                ? { href: "/runs", label: "Clear filters" }
                : { href: "/workflows", label: "Browse workflows" }
            }
          />
        ) : (
          <RunsTable runs={runs} />
        )}
      </Card>
    </>
  );
}
