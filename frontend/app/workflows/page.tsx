import { Plus } from "lucide-react";
import Link from "next/link";
import { NodeTypeBadge } from "@/components/NodeTypeBadge";
import { RelativeTime } from "@/components/RelativeTime";
import { RunStatusBadge } from "@/components/RunStatusBadge";
import { EmptyState, ErrorState, PageHeader } from "@/components/States";
import { buttonVariants } from "@/components/ui/button";
import { Badge, Card, Table, Td, Th } from "@/components/ui/primitives";
import { serverApi } from "@/lib/api/server";
import { cn } from "@/lib/utils";

export const dynamic = "force-dynamic";
export const metadata = { title: "Workflows" };

const FILTERS = [
  { value: "", label: "All" },
  { value: "published", label: "Published" },
  { value: "draft", label: "Draft" },
] as const;

export default async function WorkflowsPage({
  searchParams,
}: {
  searchParams: { status?: string };
}) {
  const status =
    searchParams.status === "draft" || searchParams.status === "published"
      ? searchParams.status
      : undefined;
  let workflows;
  try {
    workflows = await serverApi().listWorkflows(status);
  } catch (e) {
    return (
      <>
        <PageHeader title="Workflows" />
        <ErrorState error={e} />
      </>
    );
  }
  return (
    <>
      <PageHeader
        title="Workflows"
        description="Definitions built from catalog node types. Published versions are frozen; runs execute their own snapshot."
        actions={
          <Link href="/workflows/new" className={buttonVariants({ variant: "primary" })}>
            <Plus aria-hidden /> New workflow
          </Link>
        }
      />
      <nav aria-label="Filter by status" className="mb-4 flex gap-1">
        {FILTERS.map((f) => (
          <Link
            key={f.value}
            href={f.value ? `/workflows?status=${f.value}` : "/workflows"}
            aria-current={(status ?? "") === f.value ? "page" : undefined}
            className={cn(
              "rounded-md px-3 py-1.5 text-xs text-muted hover:bg-elevated hover:text-fg",
              (status ?? "") === f.value && "bg-elevated text-fg",
            )}
          >
            {f.label}
          </Link>
        ))}
      </nav>
      <Card>
        {workflows.length === 0 ? (
          <EmptyState
            title="No workflows match"
            description="Create one by pasting a definition in the seed shape, or compile one from English."
            action={{ href: "/workflows/new", label: "New workflow" }}
          />
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>Name</Th>
                <Th>Status</Th>
                <Th>Trigger</Th>
                <Th className="text-right">Version</Th>
                <Th className="text-right">Runs</Th>
                <Th>Last run</Th>
                <Th className="sr-only">Actions</Th>
              </tr>
            </thead>
            <tbody>
              {workflows.map((w) => (
                <tr key={w.id} className="hover:bg-elevated/60">
                  <Td>
                    <Link href={`/workflows/${w.id}`} className="block hover:text-accent">
                      <span className="text-sm font-medium">{w.name}</span>
                      <span className="block font-mono text-2xs text-subtle">{w.id}</span>
                    </Link>
                  </Td>
                  <Td>
                    <span className="flex flex-wrap gap-1">
                      <Badge tone={w.status === "published" ? "ok" : "neutral"}>{w.status}</Badge>
                      {w.has_unpublished_changes && w.status === "published" ? (
                        <Badge tone="warn">edited</Badge>
                      ) : null}
                    </span>
                  </Td>
                  <Td className="text-xs text-muted">{w.trigger_type}</Td>
                  <Td className="text-right font-mono text-xs">
                    {w.published_version ? `v${w.published_version}` : "—"}
                  </Td>
                  <Td className="text-right font-mono text-xs tabular-nums">{w.run_count}</Td>
                  <Td>
                    {w.last_run_status ? (
                      <span className="flex items-center gap-2">
                        <RunStatusBadge status={w.last_run_status} />
                        <RelativeTime iso={w.last_run_at} className="text-2xs text-subtle" />
                      </span>
                    ) : (
                      <span className="text-xs text-subtle">never</span>
                    )}
                  </Td>
                  <Td className="text-right">
                    <Link
                      href={`/workflows/${w.id}`}
                      className={buttonVariants({ variant: "ghost", size: "sm" })}
                    >
                      Open
                    </Link>
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      <p className="mt-4 flex flex-wrap items-center gap-2 text-2xs text-subtle">
        Node types:
        {["http_request", "condition", "delay", "notify", "ai", "approval", "order_action"].map(
          (t) => (
            <NodeTypeBadge key={t} type={t} />
          ),
        )}
      </p>
    </>
  );
}
