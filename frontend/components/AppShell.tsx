"use client";

import {
  CheckSquare,
  LayoutDashboard,
  Moon,
  PlayCircle,
  Search,
  Sun,
  Wand2,
  Workflow,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTheme } from "next-themes";
import { useEffect, useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";
import { CommandPalette } from "./CommandPalette";

const NAV = [
  { href: "/", label: "Dashboard", Icon: LayoutDashboard, keys: null },
  { href: "/workflows", label: "Workflows", Icon: Workflow, keys: "g w" },
  { href: "/runs", label: "Runs", Icon: PlayCircle, keys: "g r" },
  { href: "/approvals", label: "Approvals", Icon: CheckSquare, keys: "g a" },
  { href: "/compiler", label: "NL compiler", Icon: Wand2, keys: null },
] as const;

function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  const dark = !mounted || resolvedTheme !== "light";
  return (
    <button
      type="button"
      onClick={() => setTheme(dark ? "light" : "dark")}
      aria-label={dark ? "Switch to light mode" : "Switch to dark mode"}
      className="inline-flex size-8 items-center justify-center rounded-md text-muted hover:bg-elevated hover:text-fg"
    >
      {dark ? <Sun className="size-4" /> : <Moon className="size-4" />}
    </button>
  );
}

/** 240px left navigation + top bar; collapses to a horizontal nav under 1024px. */
export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [paletteOpen, setPaletteOpen] = useState(false);
  const active = (href: string) => (href === "/" ? pathname === "/" : pathname.startsWith(href));

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[240px_1fr]">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50"
      >
        Skip to content
      </a>
      <aside className="border-b border-border bg-surface lg:sticky lg:top-0 lg:h-screen lg:border-b-0 lg:border-r">
        <div className="flex h-14 items-center gap-2 px-5">
          <span
            aria-hidden
            className="grid size-7 place-items-center rounded-md bg-accent text-accent-fg"
          >
            <Workflow className="size-4" />
          </span>
          <span className="text-sm font-semibold tracking-tight">Relay</span>
          <span className="ml-1 rounded bg-elevated px-1.5 py-0.5 text-2xs text-subtle">
            console
          </span>
        </div>
        <nav
          aria-label="Primary"
          className="flex gap-1 overflow-x-auto px-3 pb-3 lg:flex-col lg:pb-0"
        >
          {NAV.map(({ href, label, Icon, keys }) => (
            <Link
              key={href}
              href={href}
              aria-current={active(href) ? "page" : undefined}
              className={cn(
                "group flex items-center gap-2.5 whitespace-nowrap rounded-md px-3 py-2 text-sm text-muted transition-colors hover:bg-elevated hover:text-fg",
                active(href) && "bg-elevated text-fg",
              )}
            >
              <Icon aria-hidden className={cn("size-4", active(href) && "text-accent")} />
              {label}
              {keys ? <span className="kbd ml-auto hidden lg:inline">{keys}</span> : null}
            </Link>
          ))}
        </nav>
        <p className="mt-auto hidden px-5 pb-5 pt-8 text-2xs text-subtle lg:block">
          Press <span className="kbd">/</span> to search
        </p>
      </aside>
      <div className="min-w-0">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border bg-bg/80 px-4 backdrop-blur md:px-8">
          <button
            type="button"
            onClick={() => setPaletteOpen(true)}
            className="flex h-8 w-full max-w-sm items-center gap-2 rounded-md border border-border bg-surface px-3 text-xs text-subtle hover:text-fg"
          >
            <Search className="size-3.5" aria-hidden />
            Search workflows, runs, pages…
            <span className="kbd ml-auto">/</span>
          </button>
          <div className="ml-auto">
            <ThemeToggle />
          </div>
        </header>
        <main id="main" className="mx-auto w-full max-w-page px-4 py-8 md:px-8">
          {children}
        </main>
      </div>
      <CommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} />
    </div>
  );
}
