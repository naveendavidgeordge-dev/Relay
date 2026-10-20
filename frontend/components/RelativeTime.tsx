"use client";

import { useEffect, useState } from "react";
import { absoluteTime, relativeTime } from "@/lib/utils";

/** "2m ago", with the absolute UTC time on hover; refreshes every 30 s. */
export function RelativeTime({
  iso,
  className,
}: {
  iso: string | null | undefined;
  className?: string;
}) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(t);
  }, []);
  if (!iso) return <span className={className}>—</span>;
  return (
    <time dateTime={iso} title={absoluteTime(iso)} className={className} suppressHydrationWarning>
      {relativeTime(iso, now)}
    </time>
  );
}
