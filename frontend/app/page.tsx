import { Activity, CheckSquare, Gauge, PlayCircle, Timer } from "lucide-react";
import Link from "next/link";
import { MetricCard } from "@/components/MetricCard";
import { RelativeTime } from "@/components/RelativeTime";
import { RunsTable } from "@/components/RunsTable";
import { EmptyState, ErrorState, PageHeader } from "@/components/States";
import { Card, CardHeader } from "@/components/ui/primitives";
import { serverApi } from "@/lib/api/server";
import { computeMetrics } from "@/lib/metrics";
import { formatDuration } from "@/lib/utils";

export const dynamic = "force-dynamic";

export default async function DashboardPage() {
  const api = serverApi();
  let data;
  try {
    const [runs, pending, workflows] = await Promise.all([
      api.listRuns({ limit: 200 }),
      api.listApprovals("pending"),
      api.listWorkflows(),
    ]);
    data = { runs, pending, workflows };
  } catch (e) {
    return (
      <>
        <PageHeader title="Dashboard" />
        <ErrorState error={e} />
      </>
    );
  }
  const m = computeMetrics(data.runs);
  return (
    <>
      <PageHeader
        title="Dashboard"
        description={`${data.workflows.length} workflows · ${data.workflows.filter((w) => w.status === "published").length} published`}
      />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Runs today"
          value={m.runsToday}
          icon={<PlayCircle />}
          series={m.hourly}
          hint="Last 12 hours, per hour"
        />
        <MetricCard
          label="Success rate"
          value={m.successRate === null ? "—" : `${Math.round(m.successRate * 100)}%`}
          icon={<Gauge />}
          hint="Of finished runs in the last 200"
        />
        <MetricCard
          label="p95 duration"
          value={formatDuration(m.p95DurationMs)}
          icon={<Timer />}
          hint="Succeeded runs"
        />
        <MetricCard
          label="Pending approvals"
          value={data.pending.length}
          icon={<CheckSquare />}
          hint="Waiting for a human"
        />
      </div>

      {data.pending.length > 0 ? (
        <Card className="mt-6 border-warn/40">
          <CardHeader
            title="Waiting for approval"
            description="Runs paused at an approval node"
            actions={
              <Link href="/approvals" className="text-xs text-accent hover:underline">
                Review all →
              </Link>
            }
          />
          <ul className="divide-y divide-border">
            {data.pending.slice(0, 3).map((a) => (
              <li key={a.id} className="flex items-center gap-4 px-6 py-3 text-sm">
                <span className="line-clamp-1 flex-1">{a.message}</span>
                <span className="text-2xs text-subtle">
                  <RelativeTime iso={a.created_at} />
                </span>
                <Link href={`/approvals#${a.id}`} className="text-xs text-accent hover:underline">
                  Decide
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      <Card className="mt-6">
        <CardHeader
          title="Recent runs"
          description="Newest first"
          actions={
            <Link href="/runs" className="text-xs text-accent hover:underline">
              All runs →
            </Link>
          }
        />
        {data.runs.length === 0 ? (
          <EmptyState
            title="No runs yet"
            description="Trigger a seed workflow — try wf_expense_approval with a sample payload."
            action={{ href: "/workflows/wf_expense_approval", label: "Open wf_expense_approval" }}
          />
        ) : (
          <RunsTable runs={data.runs.slice(0, 10)} />
        )}
      </Card>
      <p className="mt-4 flex items-center gap-1.5 text-2xs text-subtle">
        <Activity className="size-3" aria-hidden /> Live data from the Relay API; refresh to update.
      </p>
    </>
  );
}
