"use client";

import { Command } from "cmdk";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { browserApi } from "@/lib/api/browser";
import type { Workflow } from "@/lib/api/types";
import { Dialog, DialogContent } from "./ui/dialog";

const PAGES = [
  { href: "/", label: "Dashboard" },
  { href: "/workflows", label: "Workflows" },
  { href: "/workflows/new", label: "New workflow (paste JSON)" },
  { href: "/runs", label: "Runs" },
  { href: "/approvals", label: "Approvals" },
  { href: "/compiler", label: "NL compiler" },
  { href: "/guide", label: "User guide" },
  { href: "/docs", label: "Developer guide" },
];

function typing(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null;
  return !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
}

/**
 * ⌘K / "/" palette plus vim-style chords: `g w` workflows, `g r` runs, `g a` approvals.
 */
export function CommandPalette({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
}) {
  const router = useRouter();
  const [workflows, setWorkflows] = useState<Workflow[]>([]);
  const [query, setQuery] = useState("");
  const chord = useRef<number | null>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        onOpenChange(true);
        return;
      }
      if (typing(e.target) || e.metaKey || e.ctrlKey || e.altKey) return;
      if (e.key === "/") {
        e.preventDefault();
        onOpenChange(true);
        return;
      }
      if (e.key === "g") {
        chord.current = window.setTimeout(() => (chord.current = null), 1000);
        return;
      }
      if (chord.current !== null) {
        const dest = { w: "/workflows", r: "/runs", a: "/approvals" }[e.key];
        window.clearTimeout(chord.current);
        chord.current = null;
        if (dest) router.push(dest);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onOpenChange, router]);

  useEffect(() => {
    if (open && workflows.length === 0) {
      browserApi
        .listWorkflows()
        .then(setWorkflows)
        .catch(() => setWorkflows([]));
    }
  }, [open, workflows.length]);

  const go = (href: string) => {
    onOpenChange(false);
    setQuery("");
    router.push(href);
  };
  const looksLikeRun = /^run_[0-9a-z]{8,}$/i.test(query.trim());

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title="Search" className="p-0 [&>div:first-child]:hidden">
        <Command label="Command palette" className="text-sm">
          <Command.Input
            value={query}
            onValueChange={setQuery}
            placeholder="Type a page, workflow, or paste a run id…"
            className="h-12 w-full border-b border-border bg-transparent px-4 outline-none placeholder:text-subtle"
          />
          <Command.List className="max-h-80 overflow-auto p-2">
            <Command.Empty className="px-3 py-6 text-center text-xs text-muted">
              No results.
            </Command.Empty>
            {looksLikeRun ? (
              <Command.Group heading="Run" className="text-2xs text-subtle">
                <Command.Item
                  onSelect={() => go(`/runs/${query.trim()}`)}
                  className="cursor-pointer rounded px-3 py-2 text-sm text-fg data-[selected=true]:bg-elevated"
                >
                  Open {query.trim()}
                </Command.Item>
              </Command.Group>
            ) : null}
            <Command.Group heading="Pages" className="text-2xs text-subtle">
              {PAGES.map((p) => (
                <Command.Item
                  key={p.href}
                  onSelect={() => go(p.href)}
                  className="cursor-pointer rounded px-3 py-2 text-sm text-fg data-[selected=true]:bg-elevated"
                >
                  {p.label}
                </Command.Item>
              ))}
            </Command.Group>
            <Command.Group heading="Workflows" className="text-2xs text-subtle">
              {workflows.map((w) => (
                <Command.Item
                  key={w.id}
                  value={`${w.id} ${w.name}`}
                  onSelect={() => go(`/workflows/${w.id}`)}
                  className="cursor-pointer rounded px-3 py-2 text-sm text-fg data-[selected=true]:bg-elevated"
                >
                  <span className="font-mono text-xs">{w.id}</span>
                  <span className="ml-2 text-muted">{w.name}</span>
                </Command.Item>
              ))}
            </Command.Group>
          </Command.List>
        </Command>
      </DialogContent>
    </Dialog>
  );
}
