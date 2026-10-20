# Runbook

## Start / stop

```bash
cp .env.example .env            # set RELAY_MASTER_KEY (openssl rand -base64 32) and RELAY_DB_COLUMN_KEY outside dev
make up                         # postgres, mock-world, ai-sidecar, backend (api), worker, console
make ps                         # health
make logs
make down                       # keeps the database volume (runs survive)
```

Console: http://localhost:3000 · API: http://localhost:8080 · mock world: http://localhost:9210.

## Running without Java/Python installed

Everything can run through Docker (verified on Windows with Docker Desktop / Engine 29):

```bash
# backend unit + integration tests (Testcontainers talks to Docker through the socket)
docker run --rm -v //var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true -v "$PWD:/src" -v relay-gradle-cache:/home/gradle/.gradle -w /src gradle:8.14.3-jdk21 gradle -p backend --no-daemon test -Prelay.fixturesDir=/src/data
# smoke test / duplication check against the running stack
docker run --rm -v "$PWD:/app" -w /app python:3.11-slim python scripts/smoke_test.py --url http://host.docker.internal:8080 --token relay-demo-token --world http://host.docker.internal:9210
docker run --rm -v "$PWD:/app" -w /app python:3.11-slim python scripts/duplication_check.py --url http://host.docker.internal:9210
```

On Git Bash prefix with `MSYS_NO_PATHCONV=1`. Testcontainers ≥ 1.21.4 is required for Docker Engine 29
(older versions speak a Docker API the engine no longer accepts).

## Operating

| Situation | Action |
|---|---|
| A run looks stuck in `running` | `GET /runs/{id}`: `lease_owner` set and the step `running` means a worker is on it; a step `waiting` with `resume_at` means it is in a delay. If the worker died, the lease expires within `RELAY_LEASE_SECONDS` and any worker resumes it |
| Stop a run | `POST /runs/{id}/cancel` (cooperative between steps) |
| Mock world down | Steps retry with backoff (`RELAY_RETRY_*`), then fail with `upstream_unavailable`. Restore the world and re-trigger. Failed runs are not resumed automatically |
| Worker restarted | Nothing to do: leases are reclaimed and runs resume from persisted steps |
| Rotate crypto keys | `KeyRing.rotate("kem"\|"signing")` (maintenance task); retired keys stay readable |
| Use a real model | `AI_PROVIDER=gemini` + `GEMINI_API_KEY` (or `groq`/`ollama`), restart `ai-sidecar` |
| Demo the AI repair retry | `MOCK_AI_FIRST_INVALID=true`, restart `ai-sidecar` |

## Failure drills (record in the demo video)

```bash
# outage mid-run: steps retry with backoff, then complete after restore
curl -s -X POST localhost:9210/admin/config -d '{"mode": "down"}'
curl -s -X POST localhost:9210/admin/config -d '{"mode": "ok", "fail_rate": 0, "latency_ms": 0}'
# flaky world: retries absorb it; the ledger shows replays, not duplicates
curl -s -X POST localhost:9210/admin/config -d '{"fail_rate": 0.3}'
# slow world: the read timeout (RELAY_HTTP_READ_TIMEOUT_MS) fails the attempt, not the engine
curl -s -X POST localhost:9210/admin/config -d '{"latency_ms": 15000}'
```

---

# Verification report (template)

Fill this in after running against the compose stack. Paste raw output, not summaries.

## 1. Environment

| Item | Value |
|---|---|
| Commit | |
| Date (UTC) | |
| Stack | `make up` (compose), worker `relay-worker`, AI provider: |
| Reference hashes | `make hashes` matches `docs/reqs/README.md`: yes / no |

## 2. Smoke test

```
$ python scripts/smoke_test.py --url http://localhost:8080 --token relay-demo-token
<paste output>
```

Explain any WARN:

## 3. Kill-and-resume drill (EVALUATION_GUIDE §2)

Where the worker was killed: during `pack_delay` (~3 s after the confirmation email appeared in the ledger), `docker kill --signal KILL relay-worker`.

```
$ make drill
<paste output, including the step table and duplication_check.py output>
```

Optional second kill point (between the shipment call and its persist): covered by `EngineIT.CrashRecovery.killedBetweenSideEffectAndPersistResumesWithTheSameKey` (paste the test output).

## 4. Injection payloads

| Payload | Run | Final status | Branch | Order touched before approval? | Notes |
|---|---|---|---|---|---|
| `pay_inject_001` | | `waiting_approval` | refund_gate | no (ledger has no `order.refund`) | |
| `pay_inject_002` | | `succeeded` | notify_support | n/a | summary text: |

## 5. Schema enforcement

Run with `MOCK_AI_FIRST_INVALID=true` (or a real model with a strict schema); paste the `classify`
step's `attempts` showing `ai_attempt 1 valid=false` and `ai_attempt 2 valid=true`.

## 6. Step cap

`wf_runaway` run id, final status `failed`, `failure_reason` = `max_steps cap (12) exceeded`, `steps_executed` = 12.

## 7. NL compiler eval (Good To Have)

```
$ make nl-eval
<paste JSON>
```

Accuracy: __ / 15. Misses and why:

## 8. Idempotency scheme and the crash window

Key = `{run_id}:{node_id}` (loop revisits add `:s{sequence}`), persisted on the step **before** the
call and reused verbatim by every retry and resume. If the worker dies after the side effect but
before the step is persisted, the resumed worker re-sends with the same key and the mock world
replays (DESIGN/04).

## 9. Test suites

```
$ make test
<paste summaries: backend (unit + Testcontainers), frontend (vitest), ai-sidecar (pytest)>
```

## 10. Known limitations

Single worker in the demo (multi-worker safe by construction, not demonstrated); 1-minute schedule
granularity, no catch-up; cooperative (between-steps) cancellation; software PQ keys (no HSM);
`timeout_seconds` off by default (DESIGN/00 C8); heuristic NL compiler tuned to common phrasings.
