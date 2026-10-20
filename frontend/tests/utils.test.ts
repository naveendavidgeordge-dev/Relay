import { computeMetrics } from "@/lib/metrics";
import { formatDuration, percentile, relativeTime } from "@/lib/utils";
import type { Run } from "@/lib/api/types";

const NOW = Date.parse("2026-07-04T12:00:00Z");

function run(status: Run["status"], minutesAgo: number, duration = 1000): Run {
  return {
    run_id: `run_${minutesAgo}`,
    workflow_id: "wf",
    workflow_version: 1,
    status,
    trigger_type: "manual",
    steps_executed: 1,
    ai_tokens_used: 0,
    cancel_requested: false,
    created_at: new Date(NOW - minutesAgo * 60_000).toISOString(),
    duration_ms: duration,
  };
}

describe("utils", () => {
  it("formats durations", () => {
    expect(formatDuration(3)).toBe("3 ms");
    expect(formatDuration(1200)).toBe("1.2 s");
    expect(formatDuration(123_000)).toBe("2m 03s");
    expect(formatDuration(undefined)).toBe("—");
  });

  it("formats relative times", () => {
    expect(relativeTime(new Date(NOW - 120_000).toISOString(), NOW)).toBe("2m ago");
    expect(relativeTime(new Date(NOW).toISOString(), NOW)).toBe("just now");
  });

  it("computes percentiles", () => {
    expect(percentile([1, 2, 3, 4, 100], 95)).toBe(100);
    expect(percentile([], 95)).toBeNull();
  });

  it("computes dashboard KPIs", () => {
    const m = computeMetrics(
      [
        run("succeeded", 10, 500),
        run("failed", 20),
        run("running", 5),
        run("succeeded", 60 * 30, 9000),
      ],
      NOW,
    );
    expect(m.runsToday).toBe(3);
    expect(m.successRate).toBeCloseTo(2 / 3);
    expect(m.p95DurationMs).toBe(9000);
    expect(m.hourly).toHaveLength(12);
    expect(m.hourly.at(-1)).toBe(3);
  });
});
