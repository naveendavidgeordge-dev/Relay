"use client";

import { Check, Copy } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";

/** Copies `value` to the clipboard; used next to every id (run, approval, node). */
export function CopyButton({
  value,
  label = "Copy",
  className,
}: {
  value: string;
  label?: string;
  className?: string;
}) {
  const [done, setDone] = useState(false);
  return (
    <button
      type="button"
      aria-label={`${label}: ${value}`}
      className={cn(
        "inline-flex size-6 items-center justify-center rounded text-subtle transition-colors hover:bg-elevated hover:text-fg",
        className,
      )}
      onClick={async (e) => {
        e.stopPropagation();
        try {
          await navigator.clipboard.writeText(value);
          setDone(true);
          toast.success("Copied", { description: value });
          setTimeout(() => setDone(false), 1200);
        } catch {
          toast.error("Clipboard unavailable");
        }
      }}
    >
      {done ? <Check className="size-3.5 text-ok" /> : <Copy className="size-3.5" />}
    </button>
  );
}

/** Monospace id with a copy button. */
export function IdChip({ id, className }: { id: string; className?: string }) {
  return (
    <span
      className={cn("inline-flex items-center gap-0.5 font-mono text-xs text-muted", className)}
    >
      <span className="truncate">{id}</span>
      <CopyButton value={id} label="Copy id" />
    </span>
  );
}
