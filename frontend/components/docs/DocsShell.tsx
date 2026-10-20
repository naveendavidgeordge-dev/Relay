import { ArrowLeft, ArrowRight, BookOpen, Code2 } from "lucide-react";
import Link from "next/link";
import { slugify } from "@/lib/docs/inline";
import type { DocCollection, DocPage } from "@/lib/docs/types";
import { cn } from "@/lib/utils";
import { DocRenderer } from "./DocRenderer";
import { DocsNav } from "./DocsNav";

const SECTIONS = [
  { href: "/guide", label: "User guide", Icon: BookOpen },
  { href: "/docs", label: "Developer guide", Icon: Code2 },
] as const;

function hrefFor(c: DocCollection, index: number): string {
  const page = c.pages[index];
  return index === 0 || !page ? c.base : `${c.base}/${page.slug}`;
}

/** Shared layout for /docs and /guide: section switcher, topic tabs, content, TOC, prev/next. */
export function DocsShell({ collection, page }: { collection: DocCollection; page: DocPage }) {
  const index = collection.pages.findIndex((p) => p.slug === page.slug);
  const prev = index > 0 ? collection.pages[index - 1] : undefined;
  const next = index < collection.pages.length - 1 ? collection.pages[index + 1] : undefined;
  const toc = page.blocks.flatMap((b) => (b.t === "h2" ? [b.text] : []));

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">{collection.title}</h1>
          <p className="mt-1 text-sm text-muted">{collection.tagline}</p>
        </div>
        <div
          role="group"
          aria-label="Documentation section"
          className="inline-flex gap-1 rounded-md border border-border bg-elevated p-1"
        >
          {SECTIONS.map(({ href, label, Icon }) => (
            <Link
              key={href}
              href={href}
              aria-current={href === collection.base ? "page" : undefined}
              className={cn(
                "inline-flex items-center gap-1.5 rounded px-3 py-1 text-xs font-medium text-muted transition-colors hover:text-fg",
                href === collection.base && "bg-surface text-fg shadow-sm",
              )}
            >
              <Icon aria-hidden className="size-3.5" />
              {label}
            </Link>
          ))}
        </div>
      </div>

      <div className="grid gap-8 lg:grid-cols-[220px_minmax(0,1fr)] xl:grid-cols-[220px_minmax(0,1fr)_200px]">
        <aside className="lg:sticky lg:top-20 lg:max-h-[calc(100vh-6rem)] lg:self-start lg:overflow-y-auto">
          <DocsNav
            base={collection.base}
            items={collection.pages.map((p, i) => ({
              href: hrefFor(collection, i),
              title: p.title,
              group: p.group,
            }))}
          />
        </aside>

        <article className="min-w-0">
          <header className="mb-6">
            <p className="text-2xs font-medium uppercase tracking-wide text-accent">{page.group}</p>
            <h2 className="mt-1 text-2xl font-semibold tracking-tight">{page.title}</h2>
            <p className="mt-2 text-sm text-muted">{page.summary}</p>
          </header>

          <DocRenderer blocks={page.blocks} />

          <nav
            aria-label="Previous and next topic"
            className="mt-12 grid gap-3 border-t border-border pt-6 sm:grid-cols-2"
          >
            {prev ? (
              <Link
                href={hrefFor(collection, index - 1)}
                className="card group flex items-center gap-3 p-4 hover:border-accent/50"
              >
                <ArrowLeft aria-hidden className="size-4 text-subtle group-hover:text-accent" />
                <span>
                  <span className="block text-2xs text-subtle">Previous</span>
                  <span className="text-sm font-medium">{prev.title}</span>
                </span>
              </Link>
            ) : (
              <span />
            )}
            {next ? (
              <Link
                href={hrefFor(collection, index + 1)}
                className="card group flex items-center justify-end gap-3 p-4 text-right hover:border-accent/50"
              >
                <span>
                  <span className="block text-2xs text-subtle">Next</span>
                  <span className="text-sm font-medium">{next.title}</span>
                </span>
                <ArrowRight aria-hidden className="size-4 text-subtle group-hover:text-accent" />
              </Link>
            ) : null}
          </nav>
        </article>

        {toc.length > 1 ? (
          <aside className="hidden xl:sticky xl:top-20 xl:block xl:self-start">
            <p className="mb-2 text-2xs font-medium uppercase tracking-wide text-subtle">
              On this page
            </p>
            <ul className="space-y-1.5 border-l border-border">
              {toc.map((t) => (
                <li key={t}>
                  <a
                    href={`#${slugify(t)}`}
                    className="-ml-px block border-l border-transparent pl-3 text-xs text-muted hover:border-accent hover:text-fg"
                  >
                    {t}
                  </a>
                </li>
              ))}
            </ul>
          </aside>
        ) : null}
      </div>
    </div>
  );
}
