import type { Run } from "./api/types";
import { TERMINAL } from "./api/types";
import { percentile } from "./utils";

export interface DashboardMetrics {
  runsToday: number;
  successRate: number | null;
  p95DurationMs: number | null;
  hourly: number[];
}

/** KPIs over the most recent runs (UTC day boundaries). */
export function computeMetrics(runs: Run[], now: number = Date.now()): DashboardMetrics {
  const startOfDay = new Date(now);
  startOfDay.setUTCHours(0, 0, 0, 0);
  const runsToday = runs.filter((r) => Date.parse(r.created_at) >= startOfDay.getTime()).length;
  const terminal = runs.filter((r) => TERMINAL.has(r.status));
  const succeeded = terminal.filter((r) => r.status === "succeeded");
  const successRate = terminal.length ? succeeded.length / terminal.length : null;
  const p95DurationMs = percentile(
    succeeded.map((r) => r.duration_ms ?? 0).filter((d) => d > 0),
    95,
  );
  const hourly = Array.from({ length: 12 }, (_, i) => {
    const end = now - (11 - i) * 3_600_000;
    const start = end - 3_600_000;
    return runs.filter((r) => {
      const t = Date.parse(r.created_at);
      return t > start && t <= end;
    }).length;
  });
  return { runsToday, successRate, p95DurationMs, hourly };
}
