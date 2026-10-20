import { AlertTriangle, Info, Lightbulb } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { CopyButton } from "@/components/CopyButton";
import { parseInline, slugify } from "@/lib/docs/inline";
import type { DocBlock } from "@/lib/docs/types";
import { cn } from "@/lib/utils";

export function Inline({ text }: { text: string }) {
  return (
    <>
      {parseInline(text).map((t, i) => {
        switch (t.k) {
          case "code":
            return (
              <code
                key={i}
                className="rounded border border-border bg-elevated px-1 py-0.5 font-mono text-[0.85em] text-fg"
              >
                {t.v}
              </code>
            );
          case "bold":
            return (
              <strong key={i} className="font-semibold text-fg">
                {t.v}
              </strong>
            );
          case "link":
            return t.href.startsWith("/") || t.href.startsWith("#") ? (
              <Link
                key={i}
                href={t.href}
                className="text-accent underline-offset-2 hover:underline"
              >
                {t.v}
              </Link>
            ) : (
              <a
                key={i}
                href={t.href}
                target="_blank"
                rel="noreferrer noopener"
                className="text-accent underline-offset-2 hover:underline"
              >
                {t.v}
              </a>
            );
          default:
            return <span key={i}>{t.v}</span>;
        }
      })}
    </>
  );
}

const NOTE = {
  info: { Icon: Info, cls: "border-info/30 bg-info/10", icon: "text-info", label: "Note" },
  tip: { Icon: Lightbulb, cls: "border-ok/30 bg-ok/10", icon: "text-ok", label: "Tip" },
  warn: {
    Icon: AlertTriangle,
    cls: "border-warn/30 bg-warn/10",
    icon: "text-warn",
    label: "Careful",
  },
} as const;

function Block({ b }: { b: DocBlock }): ReactNode {
  switch (b.t) {
    case "h2":
      return (
        <h2
          id={slugify(b.text)}
          className="mt-10 scroll-mt-20 border-b border-border pb-2 text-lg font-semibold tracking-tight first:mt-0"
        >
          {b.text}
        </h2>
      );
    case "h3":
      return (
        <h3 id={slugify(b.text)} className="mt-6 scroll-mt-20 text-sm font-semibold">
          {b.text}
        </h3>
      );
    case "p":
      return (
        <p className="text-sm leading-6 text-muted">
          <Inline text={b.text} />
        </p>
      );
    case "ul":
    case "ol": {
      const List = b.t === "ul" ? "ul" : "ol";
      return (
        <List
          className={cn(
            "space-y-1.5 pl-5 text-sm leading-6 text-muted",
            b.t === "ul" ? "list-disc" : "list-decimal",
          )}
        >
          {b.items.map((it, i) => (
            <li key={i} className="pl-1">
              <Inline text={it} />
            </li>
          ))}
        </List>
      );
    }
    case "code":
      return (
        <figure className="card overflow-hidden">
          <figcaption className="flex items-center justify-between border-b border-border bg-elevated px-3 py-1.5 text-2xs text-subtle">
            <span className="font-mono">{b.title ?? b.lang ?? "text"}</span>
            <CopyButton value={b.code} label="Copy code" />
          </figcaption>
          <pre className="overflow-x-auto p-4 font-mono text-xs leading-5 text-fg">
            <code>{b.code}</code>
          </pre>
        </figure>
      );
    case "table":
      return (
        <div className="card overflow-x-auto">
          <table className="w-full border-collapse text-left text-xs">
            <thead>
              <tr>
                {b.head.map((h) => (
                  <th
                    key={h}
                    scope="col"
                    className="border-b border-border bg-elevated px-3 py-2 text-2xs font-medium uppercase tracking-wide text-subtle"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {b.rows.map((row, i) => (
                <tr key={i}>
                  {row.map((cell, j) => (
                    <td
                      key={j}
                      className="border-b border-border/60 px-3 py-2 align-top leading-5 text-muted"
                    >
                      <Inline text={cell} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    case "note": {
      const n = NOTE[b.tone];
      return (
        <aside className={cn("flex gap-3 rounded-lg border p-4", n.cls)}>
          <n.Icon aria-hidden className={cn("mt-0.5 size-4 shrink-0", n.icon)} />
          <div className="text-sm leading-6 text-muted">
            <p className="font-medium text-fg">{b.title ?? n.label}</p>
            <p>
              <Inline text={b.text} />
            </p>
          </div>
        </aside>
      );
    }
    case "steps":
      return (
        <ol className="space-y-3">
          {b.items.map((s, i) => (
            <li key={i} className="card flex gap-4 p-4">
              <span
                aria-hidden
                className="grid size-6 shrink-0 place-items-center rounded-full bg-accent text-2xs font-semibold text-accent-fg"
              >
                {i + 1}
              </span>
              <div className="min-w-0">
                <p className="text-sm font-medium">{s.title}</p>
                <p className="mt-1 text-sm leading-6 text-muted">
                  <Inline text={s.text} />
                </p>
              </div>
            </li>
          ))}
        </ol>
      );
  }
}

/** Renders a page's blocks with consistent vertical rhythm. */
export function DocRenderer({ blocks }: { blocks: DocBlock[] }) {
  return (
    <div className="space-y-4">
      {blocks.map((b, i) => (
        <Block key={i} b={b} />
      ))}
    </div>
  );
}
