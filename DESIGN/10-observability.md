# 10 — Observability

## Trace ids

`TraceIdFilter` runs before security: it accepts W3C `traceparent` or `X-Trace-Id`, or mints a
32-hex id. It echoes the id in `X-Trace-Id`, puts it in every error body, and stores it on the run
and its queue job. The worker restores it (`TraceContext.bindRun`), so the API log line for a
webhook and every step log line of that run share one `trace_id`. The AI sidecar receives it as
`X-Trace-Id` and logs it.

## Logs

JSON via logstash-logback-encoder with MDC fields `trace_id`, `run_id`, `node_id`, `worker_id`.
Every value passes through `SensitiveDataMasker`: bearer tokens, `whsec_…`, `rly_…`, provider keys,
JWTs, PEM blocks and `secret=`-style pairs are replaced with `****`, and fields named like secrets
are masked whole. Activate the `plain-logs` profile for human-readable local output.

`TraceEventLogger` (Observer on `TraceEvent`, after commit) writes one line per transition:

```
run run_01j… queued -> running (workflow wf_slow_fulfillment)
step confirm#1 (notify) started idempotency_key=run_01j…:confirm
step confirm#1 (notify) succeeded after 1 attempt(s)
```

Params and outputs are never logged. They live in the trace tables, which are the audit record.

## Metrics (Micrometer → `/actuator/prometheus`, authenticated)

| Metric | Tags |
|---|---|
| `relay.runs.transitions` | `to` |
| `relay.steps.finished` | `type`, `status` |
| `relay.steps.duration` (timer) | `type` |
| Resilience4j circuit breaker / retry / bulkhead metrics | instance |
| JVM, HikariCP, HTTP server | standard |

## Health

`/actuator/health` (liveness/readiness probes) is public; the compose healthcheck uses it. The worker
has no HTTP server. Its liveness is the queue: leases expire if it stops heart-beating.

## Audit

`audit_log` records workflow create/update/publish, run trigger/cancel and approval decisions with
actor and entity. Approval decisions are additionally ML-DSA-signed (DESIGN/05).
