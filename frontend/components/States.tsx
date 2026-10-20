import { AlertTriangle } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { RelayApiError } from "@/lib/api/client";
import { buttonVariants } from "./ui/button";

/** Empty state: small illustration, title, hint and a primary call to action. */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: ReactNode;
  action?: { href: string; label: string };
}) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-14 text-center">
      <svg width="88" height="56" viewBox="0 0 88 56" aria-hidden className="text-border">
        <rect
          x="2"
          y="8"
          width="36"
          height="20"
          rx="5"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        />
        <rect
          x="50"
          y="28"
          width="36"
          height="20"
          rx="5"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        />
        <path
          d="M38 18 H46 Q50 18 50 22 V38"
          fill="none"
          stroke="rgb(var(--accent))"
          strokeWidth="2"
          strokeDasharray="4 3"
        />
      </svg>
      <h3 className="text-sm font-semibold">{title}</h3>
      {description ? <p className="max-w-sm text-xs text-muted">{description}</p> : null}
      {action ? (
        <Link href={action.href} className={buttonVariants({ variant: "primary", size: "sm" })}>
          {action.label}
        </Link>
      ) : null}
    </div>
  );
}

/** Error state with the backend's code and trace id when available. */
export function ErrorState({ error }: { error: unknown }) {
  const e = error instanceof RelayApiError ? error : null;
  const message = error instanceof Error ? error.message : "Something went wrong";
  return (
    <div role="alert" className="card flex items-start gap-3 border-bad/40 p-5 text-sm">
      <AlertTriangle className="mt-0.5 size-4 shrink-0 text-bad" aria-hidden />
      <div>
        <p className="font-medium text-fg">Could not load this view</p>
        <p className="mt-1 text-muted">{message}</p>
        {e ? (
          <p className="mt-2 font-mono text-2xs text-subtle">
            {e.code}
            {e.traceId ? ` · trace ${e.traceId}` : ""}
          </p>
        ) : null}
      </div>
    </div>
  );
}

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        {description ? <p className="mt-1 text-sm text-muted">{description}</p> : null}
      </div>
      {actions ? <div className="flex gap-2">{actions}</div> : null}
    </div>
  );
}
