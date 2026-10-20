import type { DocPage } from "../types";

export const testing: DocPage = {
  slug: "testing",
  group: "Quality & operations",
  title: "Testing & verification",
  summary:
    "Every test layer, how to run it, and the kill-and-resume drill that proves exactly-once.",
  blocks: [
    { t: "h2", text: "Run the tests" },
    {
      t: "code",
      lang: "bash",
      code: `make test                                   # backend + frontend + sidecar
cd backend && ./gradlew test                # unit + slice + Testcontainers integration (needs Docker)
cd backend && ./gradlew test --tests '*Validation*'
cd backend && ./gradlew test -PskipIntegration     # everything that does not need Docker
cd frontend && npm test                     # Vitest + React Testing Library
cd ai-sidecar && pytest
make smoke                                  # needs the stack up
make drill                                  # kill-and-resume + duplication_check.py`,
    },
    { t: "h2", text: "Test layers" },
    {
      t: "table",
      head: ["Layer", "Tooling", "What it proves"],
      rows: [
        [
          "Unit (pure)",
          "JUnit 5, AssertJ",
          "Publish validation, template resolution (including no re-scan of payload text), the run transition table, idempotency keys, PQ crypto round trips and tamper detection, log masking, executor registry, condition/delay/AI executors, the NL compiler eval offline.",
        ],
        [
          "Adapter",
          "WireMock",
          "Exact mock-world request bodies; `Idempotency-Key` on every mutating call and never on GET; 5xx/429/timeout transient, 4xx permanent; URL aliases; SSRF guard.",
        ],
        [
          "Slice",
          "`@WebMvcTest`",
          "Controller status codes, role guards and the uniform error shape.",
        ],
        [
          "Persistence",
          "`@DataJpaTest` + Testcontainers Postgres 16 + Flyway",
          "Schema equals entities, CHECKs and partial indexes, immutable versions, queue `SKIP LOCKED` leases, secrets never stored in clear.",
        ],
        [
          "Integration",
          "`@SpringBootTest` + Testcontainers + WireMock + a fake model",
          "The HTTP contract and the engine end to end: auto-approve path, retries with one key, approve/reject, **injection parks at the gate**, **gate bypass refused**, snapshot integrity, AI repair visible in trace, `max_steps` on `wf_runaway`, cancel in every state, **kill inside the side-effect window**.",
        ],
        [
          "Sidecar",
          "pytest + FastAPI TestClient",
          "Seed classifications, injection summary does not parrot, schema validity, repair switch, invalid schema → 400.",
        ],
        [
          "Console",
          "Vitest + RTL",
          "Status badge, step timeline rules, approval confirm flow, workflow canvas branches and back-edges, API client (token, contract shape, error mapping, drift detection), KPI maths.",
        ],
        [
          "Contract",
          "`scripts/smoke_test.py`, `scripts/duplication_check.py`",
          "Run unmodified against the Compose stack.",
        ],
      ],
    },
    {
      t: "note",
      tone: "info",
      title: "Console test setup",
      text: "`vitest.config.ts` uses jsdom, globals, `tests/setup.ts`, an `@` alias for the frontend root and a stub for the `server-only` package so server modules can be unit tested.",
    },
    { t: "h2", text: "The kill-and-resume drill" },
    {
      t: "p",
      text: "This is the proof that a crashed run resumes without duplicating anything. `make drill` scripts it; the manual version:",
    },
    {
      t: "code",
      lang: "bash",
      code: `curl -X POST http://localhost:9210/admin/reset
curl -X POST http://localhost:8080/workflows/wf_slow_fulfillment/trigger \\
  -H "Authorization: Bearer relay-demo-token" -H "Content-Type: application/json" \\
  -d '{"input": {"order_id": "ord_2003", "customer_email": "lena@example.com"}}'
# wait for the confirmation email in GET :9210/admin/ledger, then during the 20 s pack_delay:
docker kill --signal KILL relay-worker
docker compose -f docker/docker-compose.yml start worker
python scripts/duplication_check.py --url http://localhost:9210
# expected: PASS: every side effect executed exactly once.`,
    },
    { t: "h2", text: "Failure drills against the mock world" },
    {
      t: "code",
      lang: "bash",
      code: `# outage: steps retry with backoff, then complete after restore
curl -s -X POST localhost:9210/admin/config -d '{"mode": "down"}'
curl -s -X POST localhost:9210/admin/config -d '{"mode": "ok", "fail_rate": 0, "latency_ms": 0}'
# flaky world: retries absorb it; the ledger shows replays, not duplicates
curl -s -X POST localhost:9210/admin/config -d '{"fail_rate": 0.3}'
# slow world: the read timeout fails the attempt, not the engine
curl -s -X POST localhost:9210/admin/config -d '{"latency_ms": 15000}'`,
    },
    { t: "h2", text: "Quality gates" },
    {
      t: "table",
      head: ["Area", "Command", "Checks"],
      rows: [
        [
          "Backend",
          "`./gradlew spotlessCheck checkstyleMain spotbugsMain`",
          "Google Java Format, Checkstyle, SpotBugs.",
        ],
        [
          "Frontend",
          "`npm --workspace frontend run lint && … run typecheck`",
          "ESLint (zero warnings), `tsc --noEmit`, Prettier via `format:check`.",
        ],
        [
          "Sidecar",
          "`ruff check app tests && mypy app`",
          "Lint and strict types; `black` for formatting.",
        ],
        ["All", "`make fmt` / `make lint`", "Apply or check everything."],
      ],
    },
  ],
};

export const observability: DocPage = {
  slug: "observability",
  group: "Quality & operations",
  title: "Observability",
  summary: "Trace ids, structured logs, metrics, health checks and the audit log.",
  blocks: [
    { t: "h2", text: "Trace ids" },
    {
      t: "p",
      text: "`TraceIdFilter` accepts a W3C `traceparent` or `X-Trace-Id`, or mints a 32-hex id. It echoes it in the `X-Trace-Id` response header, puts it in every error body and stores it on the run and its queue job. The worker restores it, so the API log line for a webhook and every step log line of that run share one `trace_id`. The AI sidecar receives it too.",
    },
    { t: "h2", text: "Logs" },
    {
      t: "ul",
      items: [
        "JSON via logstash-logback-encoder with MDC fields `trace_id`, `run_id`, `node_id`, `worker_id`.",
        "Every value passes through `SensitiveDataMasker`: bearer tokens, `whsec_…`, `rly_…`, provider keys, JWTs, PEM blocks and `secret=` pairs become `****`.",
        "Activate the `plain-logs` Spring profile for human-readable local output.",
        "Params and outputs are never logged — they live in the trace tables, which are the audit record.",
      ],
    },
    {
      t: "code",
      lang: "text",
      title: "One line per transition",
      code: `run run_01j… queued -> running (workflow wf_slow_fulfillment)
step confirm#1 (notify) started idempotency_key=run_01j…:confirm
step confirm#1 (notify) succeeded after 1 attempt(s)`,
    },
    { t: "h2", text: "Metrics" },
    {
      t: "p",
      text: "Micrometer exports to `/actuator/prometheus` (authenticated):",
    },
    {
      t: "table",
      head: ["Metric", "Tags"],
      rows: [
        ["`relay.runs.transitions`", "`to`"],
        ["`relay.steps.finished`", "`type`, `status`"],
        ["`relay.steps.duration` (timer)", "`type`"],
        ["Resilience4j circuit breaker, retry, bulkhead", "instance"],
        ["JVM, HikariCP, HTTP server", "standard"],
      ],
    },
    { t: "h2", text: "Health and audit" },
    {
      t: "ul",
      items: [
        "`/actuator/health` is public and used by the Compose healthcheck. The worker has no HTTP server; its liveness is the queue.",
        "`audit_log` records workflow create/update/publish, run trigger/cancel and approval decisions with actor and entity. Approval decisions are additionally ML-DSA signed.",
        "The console’s run page is the primary debugging view: it renders the full trace, attempts, tokens, idempotency keys and gating approvals.",
      ],
    },
  ],
};

export const troubleshooting: DocPage = {
  slug: "troubleshooting",
  group: "Quality & operations",
  title: "Troubleshooting & runbook",
  summary: "Symptom → cause → fix for the problems you are most likely to hit.",
  blocks: [
    { t: "h2", text: "Operating situations" },
    {
      t: "table",
      head: ["Situation", "Action"],
      rows: [
        [
          "A run looks stuck in `running`",
          "`GET /runs/{id}`: `lease_owner` set and the step `running` means a worker is on it; a step `waiting` with `resume_at` is a delay. If the worker died, the lease expires within `RELAY_LEASE_SECONDS` and any worker resumes it.",
        ],
        ["Stop a run", "`POST /runs/{id}/cancel` (cooperative between steps)."],
        [
          "Mock world down",
          "Steps retry with backoff, then fail with `upstream_unavailable`. Restore the world and re-trigger — failed runs are not resumed automatically.",
        ],
        [
          "Worker restarted",
          "Nothing to do: leases are reclaimed and runs resume from persisted steps.",
        ],
        [
          "Use a real model",
          "`AI_PROVIDER=gemini` plus `GEMINI_API_KEY` (or `groq` / `ollama`), then restart `ai-sidecar`.",
        ],
        ["Demo the AI repair retry", "`MOCK_AI_FIRST_INVALID=true`, restart `ai-sidecar`."],
        [
          "Rotate crypto keys",
          '`KeyRing.rotate("kem"|"signing")` (maintenance task); retired keys stay readable.',
        ],
      ],
    },
    { t: "h2", text: "Common problems" },
    {
      t: "table",
      head: ["Symptom", "Likely cause", "Fix"],
      rows: [
        [
          "Console pages say “Relay API is unreachable”",
          "Backend not running, or `RELAY_API_URL` wrong.",
          "`make ps`; check `curl localhost:8080/actuator/health`; in Compose the console must use `http://backend:8080`.",
        ],
        [
          "Runs stay `queued`",
          "No worker.",
          "Start the `worker` service (`make worker` locally). The API never executes runs.",
        ],
        [
          "Backend exits at start with a registry error",
          "A catalog node type has no executor, or an executor names an unknown type.",
          "Fix the `type()` of the executor; see [Backend](/docs/backend).",
        ],
        [
          "`worker` container will not start",
          "`relay-backend:local` image missing (`pull_policy: never`).",
          "Run `make up` so the backend image is built first.",
        ],
        [
          "Seeds call `localhost:9210` and fail in Docker",
          "Missing URL alias.",
          "Set `RELAY_URL_ALIASES=http://localhost:9210=>http://mock-world:9210` (Compose does this).",
        ],
        [
          "Port 3000, 8080 or 5432 already in use",
          "Another local service.",
          "Stop it or change the published port in `docker-compose.yml`.",
        ],
        [
          "`next build` cannot find workspace modules",
          "Installed from inside `frontend/`.",
          "Run `npm install` from the repo root.",
        ],
        [
          "Gradle wrapper missing",
          "The jar is not committed.",
          "`cd backend && gradle wrapper --gradle-version 8.10.2`.",
        ],
        [
          "Testcontainers fails",
          "Docker is not running.",
          "Start Docker, or use `./gradlew test -PskipIntegration`.",
        ],
        [
          "Webhook returns 403",
          "Wrong `X-Relay-Secret`.",
          "Use the secret defined in the workflow.",
        ],
        [
          "Docs pages 404",
          "Unknown slug.",
          "Topic URLs are `/docs/<slug>` and `/guide/<slug>`; open `/docs` or `/guide` for the list.",
        ],
      ],
    },
    { t: "h2", text: "Known limitations" },
    {
      t: "ul",
      items: [
        "A single worker is the default. Multiple workers are safe by construction but the multi-worker drill is not part of the verification.",
        "Schedule granularity is one minute; missed windows are not caught up.",
        "Mid-step abort is not supported; cancellation happens between steps.",
        "Crypto is software-only (BouncyCastle); keys are wrapped by an environment-supplied KEK, not an HSM.",
        "`limits.timeout_seconds` is off by default so human approvals are not cancelled by wall-clock time.",
        "The console has no per-user sign-in yet.",
      ],
    },
  ],
};

export const contributing: DocPage = {
  slug: "contributing",
  group: "Quality & operations",
  title: "Contributing & guardrails",
  summary: "The hard rules every change must respect, and where decisions are recorded.",
  blocks: [
    {
      t: "p",
      text: "These rules come from the repository’s `CLAUDE.md`. They protect the guarantees the product makes, so treat them as non-negotiable.",
    },
    { t: "h2", text: "Hard rules" },
    {
      t: "table",
      head: ["Rule", "Why"],
      rows: [
        [
          "**Never emit a node type outside `node_catalog.json`** — not in the engine, not in the NL compiler, not in tests meant to pass validation.",
          "The catalog is the boundary of what Relay can do.",
        ],
        [
          "**Never bypass approval gating.** The check lives in `engine/ApprovalGate` as a query over `approvals` rows. Do not move it into a prompt, flag or node param.",
          "Prompts can be manipulated; a database query cannot.",
        ],
        [
          "**Never log secrets or full prompts** unless `AI_LOG_PROMPTS=true`. Secrets go through `SensitiveDataMasker`; webhook secrets are redacted from every API response and snapshot.",
          "Logs outlive the systems they describe.",
        ],
        [
          "**Never include the attempt number in an idempotency key.** Key = `{run_id}:{node_id}`; a loop revisit appends `:s{sequence}`.",
          "Retries must reuse the key to be harmless.",
        ],
        [
          "**Persist, then act, then persist.** The step row (resolved input and key) is committed before its side effect; its output before the run advances.",
          "Makes every crash window recoverable.",
        ],
        [
          "**Never execute nodes in a request handler.** Controllers enqueue; the worker executes.",
          "Keeps requests fast and runs durable.",
        ],
        [
          "**Never edit `data/` or `scripts/`.** Open an ADR and ask instead.",
          "They are the read-only contract fixtures.",
        ],
      ],
    },
    { t: "h2", text: "When references disagree" },
    {
      t: "p",
      text: "Precedence is: smoke / duplication scripts → `API_CONTRACT` fixed contract → catalog and seeds → `DATA_MODEL` correctness → `EVALUATION_GUIDE` → narrative docs. Record every conflict in `DESIGN/00-overview.md` under “Reference conflicts and resolutions”.",
    },
    { t: "h2", text: "Coding standards at a glance" },
    {
      t: "table",
      head: ["Language", "Standards"],
      rows: [
        [
          "Java",
          "Java 21, Google Java Format, records for DTOs, sealed interfaces for events/results, constructor injection, no Lombok in `domain`, Javadoc on public APIs, Checkstyle + SpotBugs.",
        ],
        ["TypeScript", "`strict`, no `any`, Zod at API edges, ESLint + Prettier."],
        ["Python", "ruff + black + mypy (strict) on `ai-sidecar`."],
        ["JSON", "`snake_case` over the wire."],
      ],
    },
    { t: "h2", text: "Decision records" },
    {
      t: "table",
      head: ["ADR", "Decision"],
      rows: [
        ["0001", "Monorepo: Gradle backend, npm-workspace frontend, Python sidecar."],
        ["0002", "Postgres is the queue (`SKIP LOCKED` + lease + heartbeat)."],
        ["0003", "Post-quantum crypto: ML-KEM-768 + X25519 hybrid, ML-DSA-65 signatures."],
        ["0004", "Python FastAPI sidecar so the AI provider is swappable and mockable."],
        ["0005", "Seed URL aliases for Docker."],
        ["0006", "Natural-language compiler design."],
      ],
    },
    {
      t: "note",
      tone: "tip",
      title: "Definition of done for a change",
      text: "Formatted, linted and type-checked; tests added or updated and green; `docs/reqs/traceability.md` and the relevant `DESIGN/` page updated; and, for anything user-visible, the [User guide](/guide) topic updated too.",
    },
  ],
};
