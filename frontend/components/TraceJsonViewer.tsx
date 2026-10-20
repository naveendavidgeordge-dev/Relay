"use client";

import { ChevronRight } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";
import { CopyButton } from "./CopyButton";

/**
 * Collapsible, syntax-highlighted JSON with a copy button. Objects deeper than `expandDepth`
 * start collapsed so large snapshots stay scannable.
 */
export function TraceJsonViewer({
  value,
  label,
  expandDepth = 2,
  className,
}: {
  value: unknown;
  label?: string;
  expandDepth?: number;
  className?: string;
}) {
  const text = JSON.stringify(value ?? null, null, 2);
  return (
    <div className={cn("rounded-md border border-border bg-bg", className)}>
      <div className="flex items-center justify-between border-b border-border px-3 py-1.5">
        <span className="text-2xs font-medium uppercase tracking-wide text-subtle">
          {label ?? "JSON"}
        </span>
        <CopyButton value={text} label={`Copy ${label ?? "JSON"}`} />
      </div>
      <pre className="max-h-[420px] overflow-auto p-3 font-mono text-xs leading-5">
        <Node value={value ?? null} depth={0} expandDepth={expandDepth} />
      </pre>
    </div>
  );
}

function Node({
  value,
  depth,
  expandDepth,
}: {
  value: unknown;
  depth: number;
  expandDepth: number;
}) {
  if (value === null) return <span className="text-accent">null</span>;
  if (typeof value === "string") return <span className="text-ok">{JSON.stringify(value)}</span>;
  if (typeof value === "number") return <span className="text-warn">{value}</span>;
  if (typeof value === "boolean") return <span className="text-accent">{String(value)}</span>;
  if (Array.isArray(value)) {
    return (
      <Collection
        entries={value.map((v, i) => [String(i), v])}
        open="["
        close="]"
        depth={depth}
        expandDepth={expandDepth}
        isArray
      />
    );
  }
  if (typeof value === "object") {
    return (
      <Collection
        entries={Object.entries(value as Record<string, unknown>)}
        open="{"
        close="}"
        depth={depth}
        expandDepth={expandDepth}
      />
    );
  }
  return <span>{String(value)}</span>;
}

function Collection({
  entries,
  open,
  close,
  depth,
  expandDepth,
  isArray = false,
}: {
  entries: [string, unknown][];
  open: string;
  close: string;
  depth: number;
  expandDepth: number;
  isArray?: boolean;
}) {
  const [expanded, setExpanded] = useState(depth < expandDepth);
  if (entries.length === 0) return <span className="text-muted">{open + close}</span>;
  return (
    <span>
      <button
        type="button"
        aria-expanded={expanded}
        aria-label={expanded ? "Collapse" : "Expand"}
        onClick={() => setExpanded((e) => !e)}
        className="-ml-4 inline-flex w-4 items-center justify-center text-subtle hover:text-fg"
      >
        <ChevronRight className={cn("size-3 transition-transform", expanded && "rotate-90")} />
      </button>
      <span className="text-muted">{open}</span>
      {expanded ? (
        <>
          {"\n"}
          {entries.map(([k, v], i) => (
            <span key={k}>
              {"  ".repeat(depth + 1)}
              {isArray ? null : <span className="text-info">{JSON.stringify(k)}</span>}
              {isArray ? null : <span className="text-muted">: </span>}
              <Node value={v} depth={depth + 1} expandDepth={expandDepth} />
              {i < entries.length - 1 ? <span className="text-muted">,</span> : null}
              {"\n"}
            </span>
          ))}
          {"  ".repeat(depth)}
        </>
      ) : (
        <span className="text-subtle">
          {" "}
          {entries.length} {isArray ? "items" : "keys"}{" "}
        </span>
      )}
      <span className="text-muted">{close}</span>
    </span>
  );
}
