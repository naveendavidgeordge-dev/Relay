import type { ReactNode } from "react";
import { Sparkline } from "./Sparkline";

/** KPI tile: label, big value, hint, optional sparkline. */
export function MetricCard({
  label,
  value,
  hint,
  icon,
  series,
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  icon?: ReactNode;
  series?: number[];
}) {
  return (
    <section className="card flex flex-col gap-3 p-6" aria-label={label}>
      <div className="flex items-center justify-between text-xs text-muted">
        <span>{label}</span>
        <span className="text-subtle [&_svg]:size-4">{icon}</span>
      </div>
      <div className="flex items-end justify-between gap-3">
        <span className="text-2xl font-semibold tabular-nums tracking-tight">{value}</span>
        {series && series.length > 1 ? <Sparkline values={series} /> : null}
      </div>
      {hint ? <p className="text-2xs text-subtle">{hint}</p> : null}
    </section>
  );
}
