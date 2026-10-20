"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

export interface NavItem {
  href: string;
  title: string;
  group: string;
}

/**
 * Tab list for a documentation section. Each topic is its own route (`/docs/<slug>`), so tabs are
 * deep-linkable and server-rendered; this component only decides which one is active.
 */
export function DocsNav({ items, base }: { items: NavItem[]; base: string }) {
  const pathname = usePathname().replace(/\/$/, "") || "/";
  const active = (i: number, href: string) => pathname === href || (i === 0 && pathname === base);

  const groups: { name: string; entries: { item: NavItem; index: number }[] }[] = [];
  items.forEach((item, index) => {
    const g = groups.find((x) => x.name === item.group);
    if (g) g.entries.push({ item, index });
    else groups.push({ name: item.group, entries: [{ item, index }] });
  });

  return (
    <nav aria-label="Topics" className="space-y-5">
      {groups.map((g) => (
        <div key={g.name}>
          <p className="mb-1.5 px-3 text-2xs font-medium uppercase tracking-wide text-subtle">
            {g.name}
          </p>
          <ul role="tablist" aria-orientation="vertical" className="space-y-0.5">
            {g.entries.map(({ item, index }) => (
              <li key={item.href} role="presentation">
                <Link
                  href={item.href}
                  role="tab"
                  aria-selected={active(index, item.href)}
                  aria-current={active(index, item.href) ? "page" : undefined}
                  className={cn(
                    "block rounded-md px-3 py-1.5 text-sm text-muted transition-colors hover:bg-elevated hover:text-fg",
                    active(index, item.href) && "bg-elevated font-medium text-fg",
                  )}
                >
                  {item.title}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}
