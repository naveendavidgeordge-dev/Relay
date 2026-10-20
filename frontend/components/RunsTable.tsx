"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import type { Run } from "@/lib/api/types";
import { formatDuration } from "@/lib/utils";
import { CopyButton } from "./CopyButton";
import { RelativeTime } from "./RelativeTime";
import { RunStatusBadge } from "./RunStatusBadge";
import { Table, Td, Th } from "./ui/primitives";

/** Run history: a table on wide screens, stacked cards on phones. Rows open the run. */
export function RunsTable({ runs }: { runs: Run[] }) {
  const router = useRouter();
  return (
    <>
      <div className="hidden md:block">
        <Table>
          <thead>
            <tr>
              <Th>Run</Th>
              <Th>Workflow</Th>
              <Th>Status</Th>
              <Th>Trigger</Th>
              <Th className="text-right">Steps</Th>
              <Th className="text-right">Duration</Th>
              <Th>Started</Th>
            </tr>
          </thead>
          <tbody>
            {runs.map((r) => (
              <tr
                key={r.run_id}
                onClick={() => router.push(`/runs/${r.run_id}`)}
                className="cursor-pointer transition-colors hover:bg-elevated/60"
              >
                <Td>
                  <span className="inline-flex items-center gap-1">
                    <Link
                      href={`/runs/${r.run_id}`}
                      onClick={(e) => e.stopPropagation()}
                      className="font-mono text-xs text-fg hover:text-accent"
                    >
                      {r.run_id}
                    </Link>
                    <CopyButton value={r.run_id} label="Copy run id" />
                  </span>
                </Td>
                <Td>
                  <span className="text-sm">{r.workflow_name ?? r.workflow_id}</span>
                  <span className="ml-2 font-mono text-2xs text-subtle">v{r.workflow_version}</span>
                </Td>
                <Td>
                  <RunStatusBadge status={r.status} />
                </Td>
                <Td className="text-xs text-muted">{r.trigger_type}</Td>
                <Td className="text-right font-mono text-xs tabular-nums">
                  {r.steps_executed}
                  {r.max_steps ? <span className="text-subtle">/{r.max_steps}</span> : null}
                </Td>
                <Td className="text-right font-mono text-xs tabular-nums">
                  {formatDuration(r.duration_ms)}
                </Td>
                <Td className="text-xs text-muted">
                  <RelativeTime iso={r.created_at} />
                </Td>
              </tr>
            ))}
          </tbody>
        </Table>
      </div>
      <ul className="divide-y divide-border md:hidden">
        {runs.map((r) => (
          <li key={r.run_id}>
            <Link
              href={`/runs/${r.run_id}`}
              className="flex flex-col gap-1.5 px-4 py-3 hover:bg-elevated/60"
            >
              <span className="flex items-center justify-between gap-2">
                <span className="truncate text-sm">{r.workflow_name ?? r.workflow_id}</span>
                <RunStatusBadge status={r.status} />
              </span>
              <span className="flex items-center justify-between font-mono text-2xs text-subtle">
                <span className="truncate">{r.run_id}</span>
                <RelativeTime iso={r.created_at} />
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
