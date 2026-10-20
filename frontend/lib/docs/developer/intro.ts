import type { DocPage } from "../types";

export const overview: DocPage = {
  slug: "overview",
  group: "Introduction",
  title: "Project overview",
  summary: "What Relay is, what is in the repository, and the technology behind each part.",
  blocks: [
    {
      t: "p",
      text: "Relay is a durable AI workflow orchestrator. A workflow is a JSON definition of typed nodes (the allowed types are listed in `data/node_catalog.json`) connected by `next` / `on_true` / `on_false`. Runs execute durably on a Postgres-backed queue, `ai` nodes are schema-enforced, `approval` nodes pause runs, and `requires_approval` nodes (`order_action`) are gated by the engine. Side effects carry an `Idempotency-Key`, so a crashed-and-resumed run never duplicates them.",
    },
    { t: "h2", text: "Repository layout" },
    {
      t: "table",
      head: ["Path", "What lives there"],
      rows: [
        [
          "`backend/`",
          "Spring Boot 3 / Java 21 — the API **and** the worker. One image, started twice (profile `api` vs `worker`).",
        ],
        ["`frontend/`", "This console: Next.js 14 App Router, TypeScript, Tailwind."],
        [
          "`ai-sidecar/`",
          "FastAPI adapter over the AI providers: gemini, groq, ollama, openai-compatible and a deterministic mock.",
        ],
        [
          "`data/`, `scripts/`",
          "**Read-only** fixtures from the capstone pack: node catalog, seed workflows, sample payloads, mock world, smoke test.",
        ],
        [
          "`docker/`",
          "Dockerfiles for backend, frontend and sidecar; `docker-compose.yml`; nginx TLS config; the kill-and-resume drill script.",
        ],
        [
          "`docs/`",
          "`reqs/` (verbatim requirement documents), `adr/` (decision records), `api.md`, `runbook.md`, `guides/`.",
        ],
        [
          "`DESIGN/`",
          "Eleven design documents covering architecture, data model, state machine, idempotency, security, AI, approvals, errors, testing, observability.",
        ],
        ["`Makefile`", "Task runner — `make help` lists every target."],
      ],
    },
    { t: "h2", text: "Technology stack" },
    {
      t: "table",
      head: ["Part", "Technology", "Why"],
      rows: [
        [
          "Backend",
          "Java 21, Spring Boot 3, Gradle, Flyway, Resilience4j, Micrometer",
          "Strong typing for the engine; sealed interfaces and records model results and events.",
        ],
        [
          "Database / queue",
          "PostgreSQL 16 (`FOR UPDATE SKIP LOCKED`, pgcrypto)",
          "One durable store for state **and** the work queue — see ADR-0002.",
        ],
        [
          "Crypto",
          "BouncyCastle: ML-KEM-768, X25519, ML-DSA-65, AES-256-GCM",
          "Post-quantum protection for secrets and signed decisions.",
        ],
        [
          "AI sidecar",
          "Python 3.11, FastAPI",
          "Swappable, mockable provider layer — see ADR-0004.",
        ],
        [
          "Console",
          "Next.js 14, React 18, Tailwind, Radix UI, Zod, Vitest",
          "Server components for data, a same-origin proxy for the token.",
        ],
        [
          "Packaging",
          "Docker, Docker Compose v2, optional nginx (TLS 1.3)",
          "One command brings up the whole stack.",
        ],
      ],
    },
    { t: "h2", text: "The fixed contract" },
    {
      t: "p",
      text: "Some things are deliberately immutable because the official smoke test binds them. Never rename or “improve” them:",
    },
    {
      t: "ul",
      items: [
        "**Definition shape:** `id`, `name`, `trigger`, `entry`, `limits`, `nodes[]` with `next` (or `on_true` / `on_false` on `condition` nodes).",
        "**Routes:** `GET /workflows`, `POST /workflows`, `POST /workflows/{id}/publish`, `POST /workflows/{id}/trigger`, `POST /hooks/{id}`, `GET /runs/{id}`, `GET /approvals?status=pending`, `POST /approvals/{id}/approve`.",
        "**Run statuses, exactly:** `queued`, `running`, `waiting_approval`, `succeeded`, `failed`, `cancelled`.",
        "**Header:** `X-Relay-Secret` for webhooks (wrong or missing → 401/403).",
        "**Node and trigger types:** only what `data/node_catalog.json` lists.",
      ],
    },
    {
      t: "note",
      tone: "info",
      title: "Reading order for a new developer",
      text: "[Architecture](/docs/architecture) → [Local setup](/docs/setup) → [Backend](/docs/backend) → [Workflow engine](/docs/engine) → [Frontend](/docs/frontend) → [Testing](/docs/testing).",
    },
  ],
};

export const architecture: DocPage = {
  slug: "architecture",
  group: "Introduction",
  title: "Architecture",
  summary: "Processes, how they talk, the hexagonal backend layout and the request lifecycle.",
  blocks: [
    { t: "h2", text: "Processes" },
    {
      t: "code",
      title: "Runtime topology",
      lang: "text",
      code: `  External caller ──POST /hooks/:id (X-Relay-Secret)──┐
  Console (Next.js) ──Bearer token via route handler──┤
  smoke_test.py / curl ───────────────────────────────┤
                                                      ▼
                                       ┌──────────────────────────┐
                                       │ API  (Spring Boot, api)  │  never executes a node
                                       └────────────┬─────────────┘
                                                    │ INSERT run + queue_job (one txn)
                                                    ▼
                                       ┌──────────────────────────┐
                                       │ PostgreSQL 16            │  workflows · runs · steps
                                       │ (state AND queue)        │  approvals · queue_jobs
                                       └────────────┬─────────────┘
                                                    │ SELECT … FOR UPDATE SKIP LOCKED
                                                    ▼
                                       ┌──────────────────────────┐
                                       │ Worker (same image,      │  never serves HTTP
                                       │ WORKER_ONLY=true)        │
                                       └──────┬──────────────┬────┘
                      persist step, then act  │              │ POST /classify (timeout + breaker)
                      Idempotency-Key         ▼              ▼
                                    ┌────────────────┐  ┌────────────────┐
                                    │ Mock world     │  │ AI sidecar     │
                                    │ :9210 ledger   │  │ FastAPI :8090  │
                                    └────────────────┘  └────────────────┘`,
    },
    {
      t: "table",
      head: ["Process", "Image / entry", "Role", "Talks to"],
      rows: [
        [
          "`backend`",
          "`relay-backend`, profile `api`",
          "HTTP API, seeding, validation, triggers (enqueue only), approvals, cancel, compiler.",
          "Postgres",
        ],
        [
          "`worker`",
          "`relay-backend`, `WORKER_ONLY=true`, profile `worker`",
          "Claims queue jobs, executes nodes, fires schedules. No HTTP server.",
          "Postgres, mock world, AI sidecar",
        ],
        [
          "`ai-sidecar`",
          "Python 3.11 FastAPI",
          "`/classify`: one prompt → a JSON answer plus token counts.",
          "Model provider",
        ],
        [
          "`mock-world`",
          "`scripts/mock_world.py` (unmodified)",
          "Side-effect target with an idempotency-aware ledger.",
          "—",
        ],
        [
          "`frontend`",
          "Next.js 14 standalone",
          "Console. Server components read the API; a same-origin proxy holds the token.",
          "`backend`",
        ],
        ["`postgres`", "`postgres:16-alpine`", "Everything durable, including the queue.", "—"],
        [
          "`nginx` (optional)",
          "`nginx:1.27-alpine`",
          "TLS 1.3 front door.",
          "`backend`, `frontend`",
        ],
      ],
    },
    {
      t: "note",
      tone: "warn",
      title: "The core rule",
      text: "Do not execute runs inside the HTTP request handler. Triggers enqueue; the worker executes. The API process cannot execute nodes even by accident: the worker lifecycle bean only exists when `relay.worker.enabled=true`, and the `api` profile sets it to false.",
    },
    { t: "h2", text: "Hexagonal backend layout" },
    {
      t: "code",
      title: "Package responsibilities (backend/src/main/java/com/relay)",
      lang: "text",
      code: `api          driving adapters: controllers, DTOs, GlobalExceptionHandler
application  WorkflowService · TriggerService · ApprovalService · CancelService
             RunQueryService · SeedLoader · schedule sync/firer · compiler/*
domain       pure: catalog, workflow model, validation (Chain of Responsibility),
             run state machine, template syntax, error codes
engine       RunExecutor, NodeInvoker, ApprovalGate, TemplateResolver,
             IdempotencyKeys, worker loop
nodes        one NodeExecutor per catalog type (Strategy)
persistence  JPA entities/repositories, JdbcWorkQueue, queries
integrations OutboundHttp, MockWorldClient (anti-corruption layer), AiSidecarClient
security     auth, JWT, PQ crypto, key ring, secret vault, SensitiveDataMasker
config       Spring configuration and properties
support      shared utilities (trace ids, ids, clocks)`,
    },
    {
      t: "p",
      text: "`domain` has no Spring or JPA imports apart from Spring’s `CronExpression`. `PublishValidator` is a plain object reused by the API, the seed loader and the NL compiler.",
    },
    { t: "h2", text: "Pattern catalogue" },
    {
      t: "table",
      head: ["Pattern", "Where", "Why"],
      rows: [
        [
          "Hexagonal (ports & adapters)",
          "`WorkQueue`, `AiModelPort`, `WebhookSecretStore`, `PayloadSigner`, `PqCryptoService`",
          "Engine is tested with Postgres + WireMock + a fake model; providers swap without touching it.",
        ],
        [
          "Strategy",
          "`nodes/*NodeExecutor`, `NodeExecutorRegistry`",
          "One class per catalog type; startup fails on a missing or unknown executor.",
        ],
        [
          "State",
          "`domain.run.RunStatus`",
          "Each status decides its successors; the transition table is code, tested exhaustively.",
        ],
        [
          "Template Method",
          "`AbstractWorkerLoop` → `QueueWorker`",
          "acquire → execute → acknowledge with error containment in the skeleton.",
        ],
        [
          "Repository",
          "Spring Data repositories, `JdbcWorkQueue`",
          "SQL where it matters (`SKIP LOCKED`).",
        ],
        [
          "Outbox-style intent record",
          "`RunExecutor.plan`",
          "The step is persisted before the side effect, making the crash window recoverable.",
        ],
        [
          "Circuit breaker / Bulkhead / Retry",
          "`OutboundHttp`, `AiSidecarClient`, `NodeInvoker`",
          "A dead dependency fails fast; a slow one cannot consume every worker slot.",
        ],
        [
          "Chain of Responsibility",
          "`domain.validation.rules.*`",
          "Each rule appends violations; the shape rule can halt the chain.",
        ],
        [
          "Anti-Corruption Layer",
          "`MockWorldClient`",
          "Mock-world routes and error envelopes never leak into the engine.",
        ],
      ],
    },
    { t: "h2", text: "Request lifecycle: webhook to side effect" },
    {
      t: "steps",
      items: [
        {
          title: "Webhook arrives",
          text: "`POST /hooks/wf_expense_approval` → `TriggerController` → `TriggerService.webhook` checks the secret (SHA-256, constant time).",
        },
        {
          title: "Snapshot and enqueue",
          text: "The latest published version is snapshotted, signed (ML-DSA-65), and the run (`queued`) and its queue job are inserted **in one transaction**. The response is `202 {run_id}`.",
        },
        {
          title: "Worker claims the job",
          text: "`FOR UPDATE SKIP LOCKED` plus a lease. `RunExecutor.advance` verifies the snapshot signature and moves the run to `running`.",
        },
        {
          title: "Per node: plan → act → record",
          text: "Plan (transaction), act (no transaction, with retries), record (transaction). See [Workflow engine](/docs/engine).",
        },
        {
          title: "Approval pauses",
          text: "An approval node deletes the queue job and sets `waiting_approval`; `POST /approvals/{id}/approve` re-enqueues the run.",
        },
        {
          title: "Delays reschedule",
          text: "A `delay` node reschedules the job to `resume_at`; no thread sleeps.",
        },
      ],
    },
  ],
};
