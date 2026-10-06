# 01 — Architecture

## Processes

| Process | Image | Role | Talks to |
|---|---|---|---|
| `backend` | `relay-backend` (profile `api`) | HTTP API, seeding, validation, triggers (enqueue only), approvals, cancel, compiler | Postgres |
| `worker` | `relay-backend` (`WORKER_ONLY=true`, profile `worker`) | Claims queue jobs, executes nodes, fires schedules. No HTTP server. | Postgres, mock world, AI sidecar |
| `ai-sidecar` | Python 3.11 FastAPI | `/classify`: one prompt → JSON answer + tokens, over gemini / groq / ollama / openai-compatible / mock | model provider |
| `mock-world` | `scripts/mock_world.py` (unmodified) | Side-effect target with an idempotency-aware ledger | — |
| `frontend` | Next.js 14 standalone | Console; server components read the API, a same-origin proxy holds the token | `backend` |
| `postgres` | postgres:16 | Everything durable, including the queue | — |
| `nginx` (optional) | nginx | TLS 1.3 front door | `backend`, `frontend` |

"Do not execute runs inside the HTTP request handler. Triggers enqueue; the worker executes." —
Case Study. In Relay the API process cannot execute nodes even by accident: `WorkerLifecycle` only
exists when `relay.worker.enabled=true`, and the `api` profile sets it to false.

## Hexagonal layout (backend)

```
              ┌──────────────── api (driving adapters) ───────────────┐
              │ WorkflowController TriggerController RunController … │
              └──────────────┬────────────────────────────────────────┘
                             ▼
   application  WorkflowService · TriggerService · ApprovalService · CancelService
                RunQueryService · SeedLoader · ScheduleSync/Firer · compiler/*
                             │
          ┌──────────────────┼───────────────────────────────┐
          ▼                  ▼                               ▼
   domain (pure)        engine (core)                   security
   catalog, workflow,   RunExecutor, NodeInvoker,       PqCryptoService, KeyRing,
   validation (CoR),    ApprovalGate, TemplateResolver, EnvelopeCipher, signer,
   run state machine,   IdempotencyKeys, worker loop    secret store, auth, masker
   template syntax      ports: WorkQueue                 ports: WebhookSecretStore,
                                                          PayloadSigner
          │                  │                               │
          ▼                  ▼                               ▼
   persistence (driven adapters): JPA entities/repos, JdbcWorkQueue, queries
   nodes (strategies): one NodeExecutor per catalog type
   integrations (driven adapters): OutboundHttp, MockWorldClient (ACL), AiSidecarClient (AiModelPort)
```

`domain` has no Spring or JPA imports except Spring's `CronExpression`. `PublishValidator` is a
plain object reused by the API, the seed loader and the NL compiler.

## Pattern catalogue

| Pattern | Where | Why |
|---|---|---|
| Hexagonal (ports & adapters) | `WorkQueue`, `AiModelPort`, `WebhookSecretStore`, `PayloadSigner`, `PqCryptoService` | The engine is tested with Postgres + WireMock + a fake model; providers swap without touching the engine |
| Strategy | `nodes/*NodeExecutor`, `NodeExecutorRegistry` | One class per catalog type; the registry refuses executors for non-catalog types and fails startup on a missing one |
| State | `domain.run.RunStatus` (+ `RunEvent`) | Each status decides its successors; the transition table is code, tested exhaustively |
| Template Method | `engine.worker.AbstractWorkerLoop` → `QueueWorker` | acquire → execute (persists) → acknowledge, with error containment in the skeleton |
| Repository | Spring Data repositories, `JdbcWorkQueue` | Persistence behind interfaces; SQL where it matters (`SKIP LOCKED`) |
| Outbox-style intent record | `RunExecutor.plan` persists the step (input + key) before the side effect | The crash window becomes recoverable: on resume the intent is replayed with the same key |
| Circuit breaker | `OutboundHttp` (`mockWorld`, `http-<host>`), `AiSidecarClient` (`aiSidecar`) | A down dependency fails fast; an open circuit is retried with backoff, not hammered |
| Bulkhead | `NodeInvoker` (`node-<type>`) | A slow AI provider cannot consume every worker slot |
| Retry + backoff + jitter | `NodeInvoker` (Resilience4j Retry, exponential random backoff) | Transient failures only; never schema failures |
| Adapter | `AiSidecarClient` ↔ Python providers | One `/classify` contract over five providers |
| Builder | `NodeExecutionContext.builder()` | Many optional fields, immutable result |
| Chain of Responsibility | `domain.validation.rules.*` | Each rule appends violations; the shape rule can halt the chain |
| Observer | `TraceEvent` → `TraceEventLogger` (`@TransactionalEventListener`) | Logs/metrics after commit; ready for an SSE live view |
| Anti-Corruption Layer | `MockWorldClient` | Mock-world routes, bodies and error envelopes never leak into the engine |

## Request lifecycle (webhook → side effect)

1. `POST /hooks/wf_expense_approval` → `TriggerController` → `TriggerService.webhook` checks the
   secret (SHA-256, constant time), snapshots the latest published version, signs it (ML-DSA-65),
   inserts the run (`queued`) and its queue job **in one transaction**, returns `202 {run_id}`.
2. A worker claims the job (`FOR UPDATE SKIP LOCKED`, lease), `RunExecutor.advance` verifies the
   snapshot signature and moves the run to `running`.
3. Per node: plan (txn) → act (no txn, with retries) → record (txn). See DESIGN/04.
4. An approval node removes the job (`waiting_approval`); `POST /approvals/{id}/approve` re-enqueues.
5. A delay node reschedules the job to `resume_at`; no thread sleeps.
