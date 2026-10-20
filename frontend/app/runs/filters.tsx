"use client";

import { useRouter } from "next/navigation";
import { RUN_STATUSES } from "@/lib/api/types";

type Current = { status: string; workflow_id: string; from: string; to: string };

const field = "h-8 rounded-md border border-border bg-surface px-2 text-xs text-fg";

/** Status / workflow / date-range filters, kept in the URL so views are shareable. */
export function RunFilters({
  workflows,
  current,
}: {
  workflows: { id: string; name: string }[];
  current: Current;
}) {
  const router = useRouter();
  const update = (patch: Partial<Current>) => {
    const next = { ...current, ...patch };
    const qs = new URLSearchParams(Object.entries(next).filter(([, v]) => v !== ""));
    router.push(`/runs${qs.toString() ? `?${qs}` : ""}`);
  };
  return (
    <form
      className="flex flex-wrap items-end gap-3"
      onSubmit={(e) => e.preventDefault()}
      aria-label="Filter runs"
    >
      <label className="flex flex-col gap-1 text-2xs text-subtle">
        Status
        <select
          className={field}
          value={current.status}
          onChange={(e) => update({ status: e.target.value })}
        >
          <option value="">All statuses</option>
          {RUN_STATUSES.map((s) => (
            <option key={s} value={s}>
              {s.replace("_", " ")}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1 text-2xs text-subtle">
        Workflow
        <select
          className={field}
          value={current.workflow_id}
          onChange={(e) => update({ workflow_id: e.target.value })}
        >
          <option value="">All workflows</option>
          {workflows.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1 text-2xs text-subtle">
        From
        <input
          type="date"
          className={field}
          value={current.from}
          onChange={(e) => update({ from: e.target.value })}
        />
      </label>
      <label className="flex flex-col gap-1 text-2xs text-subtle">
        To
        <input
          type="date"
          className={field}
          value={current.to}
          onChange={(e) => update({ to: e.target.value })}
        />
      </label>
    </form>
  );
}
