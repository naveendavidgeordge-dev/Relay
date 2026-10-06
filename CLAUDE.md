# CLAUDE.md — guardrails for working in the Relay repo

## What this project is

Relay is an AI workflow orchestrator: workflows are JSON definitions of typed nodes
(`data/node_catalog.json`) connected by `next` / `on_true` / `on_false`. Runs execute durably on a
Postgres-backed queue, `ai` nodes are schema-enforced, `approval` nodes pause runs, and
`requires_approval` nodes (`order_action`) are gated by the engine. Side effects carry
`Idempotency-Key` so a crashed-and-resumed run never duplicates them.

Monorepo:

| Path | What |
|---|---|
| `backend/` | Spring Boot 3 / Java 21 API **and** worker (same image, profile `api` vs `worker`) |
| `frontend/` | Next.js 14 App Router console |
| `ai-sidecar/` | FastAPI adapter over gemini / groq / ollama / mock |
| `data/`, `scripts/` | **Read-only fixtures** from the capstone pack |
| `docs/reqs/` | Verbatim requirement documents + hashes + traceability |
| `DESIGN/`, `docs/adr/` | Design docs and ADRs |

## The immutable fixed contract — never change, rename, or "improve"

Source: `docs/reqs/API_CONTRACT.md` → "Fixed Contract". `scripts/smoke_test.py` binds it.

- Definition shape: `id`, `name`, `trigger`, `entry`, `limits`, `nodes[]` with `next`
  (or `on_true` / `on_false` on `condition` nodes).
- Routes: `GET /workflows`, `POST /workflows`, `POST /workflows/{id}/publish`,
  `POST /workflows/{id}/trigger` (body `{"input": {...}}` → `run_id`),
  `POST /hooks/{id}` (header `X-Relay-Secret`, wrong/missing → 401/403),
  `GET /runs/{id}` (`status`, `steps[].node_id`, `steps[].status`),
  `GET /approvals?status=pending` (`id`, `run_id`), `POST /approvals/{id}/approve`.
- Run statuses, exactly: `queued`, `running`, `waiting_approval`, `succeeded`, `failed`, `cancelled`.
- Header name: `X-Relay-Secret`.
- Node/trigger types: only what `data/node_catalog.json` lists.

Precedence when references disagree: smoke/duplication scripts → API_CONTRACT fixed contract →
catalog + seeds → DATA_MODEL correctness → EVALUATION_GUIDE → narrative docs. Record every
conflict in `DESIGN/00-overview.md` → "Reference conflicts and resolutions".

## Hard rules

- **Never emit a node type outside `node_catalog.json`.** Not in the engine, not in the NL
  compiler, not in tests that are meant to pass validation.
- **Never bypass approval gating.** The check lives in `engine/ApprovalGate` and is a query over
  `approvals` rows. Do not move it into a prompt, a flag, or a node param.
- **Never log secrets or full prompts** unless `AI_LOG_PROMPTS=true`. Webhook secrets, API keys,
  tokens and private keys go through `SensitiveDataMasker`; webhook secrets are redacted from every
  API response and from run snapshots.
- **Never include the attempt number in an idempotency key.** Key = `{run_id}:{node_id}`; a loop
  revisit appends `:s{sequence}`. Retries and resumes reuse the persisted key.
- **Persist, then act, then persist.** A step row (with resolved input + idempotency key) is
  committed before its side effect fires; its output is committed before the run advances.
- **Never execute nodes in a request handler.** Controllers enqueue; the worker executes.
- **Never edit `data/` or `scripts/`.** Open an ADR and ask the user instead.

## Where node types are registered / how to add one

1. The type must already exist in `data/node_catalog.json` (the catalog is the boundary; adding to
   it is a contract change that needs the user's sign-off).
2. Implement `com.relay.nodes.NodeExecutor` in `backend/src/main/java/com/relay/nodes/<Type>NodeExecutor.java`.
   Copy param names, enums and defaults verbatim from the catalog into a params record.
3. Annotate it `@Component`; `NodeExecutorRegistry` collects every bean by `type()` and fails
   startup if a catalog type has no executor or an executor names an unknown type.
4. Throw `TransientNodeException` for retryable failures (5xx, 429, timeouts) and
   `NodeFailureException` for permanent ones; return `NodeResult.completed(...)`,
   `NodeResult.awaitApproval(...)` or `NodeResult.sleepUntil(...)`.
5. Tests: unit test the executor (WireMock for HTTP), add a publish-validation case, update
   `docs/reqs/traceability.md`, `DESIGN/`, and the console's `NodeTypeBadge`.

The project-local skill `.claude/skills/relay/SKILL.md` walks through this (and the drill, and explaining a run).

## Coding standards

- **Java:** Java 21, Google Java Format (`./gradlew spotlessApply`), records for DTOs, sealed
  interfaces for events/results, constructor injection only, no Lombok in `domain`, Javadoc on public
  APIs. Checkstyle + SpotBugs run in `./gradlew check`.
- **TypeScript:** `strict`, no `any`, Zod at API edges, ESLint + Prettier.
- **Python:** ruff + black + mypy (strict) on `ai-sidecar`.
- JSON over the wire is `snake_case`.

## Running tests locally

```bash
make test               # backend + frontend + sidecar
cd backend && ./gradlew test                     # unit + slice + Testcontainers integration (needs Docker)
cd backend && ./gradlew test --tests '*Validation*'
cd frontend && npm test
cd ai-sidecar && pytest
make smoke              # needs the stack up
make drill              # kill-and-resume + duplication_check.py
```
