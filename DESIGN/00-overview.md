# 00 — Overview

## What Relay is

Relay is a workflow orchestrator for the AI era. A workflow is a JSON definition of typed nodes
taken from a closed catalog (`data/node_catalog.json`). Runs execute on background workers off a
durable Postgres queue. Every step is persisted before the run advances, side effects carry stable
idempotency keys, `ai` nodes are validated against a JSON Schema, and nodes the catalog marks
`requires_approval` are gated by the engine itself.

The design goal is a single sentence from the case study:

> "Runs must survive crashes, side effects must never fire twice, and an AI step's output must be
> structurally valid before anything downstream trusts it." — `Case_Study.md`, Background & Objective

## Document map

| Doc | Topic |
|---|---|
| [01-architecture](01-architecture.md) | Processes, hexagonal layers, package map, pattern catalogue |
| [02-data-model](02-data-model.md) | DDL, indexes, entity relationships |
| [03-run-state-machine](03-run-state-machine.md) | Six states, transition table, who triggers each edge |
| [04-idempotency-and-exactly-once](04-idempotency-and-exactly-once.md) | Key scheme, crash windows, resume algorithm |
| [05-security-and-crypto](05-security-and-crypto.md) | Threat model, hybrid PQ KEM, ML-DSA signatures, secrets |
| [06-ai-node-design](06-ai-node-design.md) | Prompt → model → parse → validate → repair |
| [07-approval-gating](07-approval-gating.md) | Approval lifecycle and the engine gate |
| [08-error-taxonomy](08-error-taxonomy.md) | Error codes, HTTP mapping, step failure records |
| [09-testing-strategy](09-testing-strategy.md) | Test pyramid, what proves what |
| [10-observability](10-observability.md) | Structured logs, trace ids, metrics |

## Principles

1. **The contract is the product.** The fixed routes, definition shape, status set and header are
   frozen (`CLAUDE.md`). Anything else may evolve if it is documented in `docs/api.md`.
2. **The database is the source of truth.** No run state lives only in memory. A worker is a
   stateless function from `(run row, step rows, snapshot)` to "the next persisted fact".
3. **Persist, then act, then persist.** Intent (resolved input + idempotency key) is committed before
   a side effect; the result is committed before the next node.
4. **Guardrails are data, not prompts.** Approval gating and the step cap are SQL-backed engine
   checks. Nothing a model outputs can create an approval row.
5. **Untrusted by default.** Trigger bodies are data. They are interpolated into prompts and params by
   path lookup only — never evaluated.

## Scope tiers

| Tier | Items | Status |
|---|---|---|
| Must Have | Catalog + seeds, CRUD + publish validation, snapshots, webhook + manual triggers, deterministic nodes, templates, AI node with schema + repair, durable queue/worker, crash resume, idempotency, approvals + engine gate, retries/backoff, max_steps cap, traces, console, smoke test | in progress — see `docs/reqs/step-log.md` |
| Good To Have | NL compiler + eval, schedule trigger, token cap, immutable version history, live view | planned after Must Have is green |
| Stretch | Multi-worker drill, agent node, fan-out | out of scope unless time permits |

## Reference conflicts and resolutions

Precedence (from the brief): smoke/duplication scripts → API_CONTRACT fixed contract → catalog + seeds → DATA_MODEL correctness → EVALUATION_GUIDE → Case Study / Implementation Guide.
seeds → DATA_MODEL correctness → EVALUATION_GUIDE → Case Study / Implementation Guide. Items marked
**OPEN** are waiting for a decision from the project owner; the implementation follows the stated
interim resolution and is isolated behind configuration so it can be flipped.

| # | Conflict | Sources | Resolution | State |
|---|---|---|---|---|
| C1 | The brief lists `Case_Study.pdf`; the pack ships `docs/reqs/Case_Study.md` (byte-identical to `RELAY_PROBLEM_STATEMENT.md`) and no PDF. | Brief §16 vs repo | Treat `Case_Study.md` as the case study. Hash recorded in `docs/reqs/README.md`. | resolved |
| C2 | `wf_slow_fulfillment.create_shipment` and `wf_runaway.check_order` hardcode `http://localhost:9210`. Inside docker-compose `localhost` is the backend container, not the mock world. The seed file's own `_comment` says "MOCK_WORLD_URL … should be engine configuration". | `seed_workflows.json` vs brief §10 (compose topology) | Fixtures are not edited. The `http_request` executor applies configurable prefix aliases at dispatch time (`RELAY_URL_ALIASES=http://localhost:9210=>http://mock-world:9210`). The stored definition, run snapshot and `resolved_input.url` keep the original URL; the trace adds `effective_url`. Running outside Docker needs no alias. | resolved (approved by owner at step-6 checkpoint) |
| C3 | The brief asks publish to reject `cyclic_entry`; API_CONTRACT says "Backward jumps (loops) are legal and must not be rejected" and the brief itself says "Never reject loops at publish". | Brief §2.1 vs API_CONTRACT §1 | Interpreted narrowly: `cyclic_entry` = *no terminal node is reachable from `entry`* (every path loops forever, so no run can ever succeed). Ordinary loops with an exit (e.g. `wf_runaway`, which can reach `done`) are never flagged. Default severity is **warning** (`relay.validation.cyclic-entry=warn`); set to `reject` to make it a 422. | resolved (owner approved: warn by default) |
| C4 | The brief says `PUT /workflows/{id}` is draft-only and published definitions are frozen; EVALUATION_GUIDE "Snapshot integrity" expects to *edit and republish* a workflow while a run is paused. | Brief §2.1 vs EVALUATION_GUIDE "Additional Checks" | `PUT` edits the workflow's **working copy** only. Each publish freezes the working copy into an immutable `workflow_versions` row (N+1). The workflow stays `published` (runnable from its latest version) while `has_unpublished_changes=true`. Runs always execute their own snapshot. | resolved |
| C5 | Brief CI uses `pnpm`; brief layout says npm workspaces. | Brief §1 vs §10 | npm workspaces everywhere (npm ships with Node; no extra tool). | resolved |
| C6 | Loop revisits: API_CONTRACT names `{run_id}:{node_id}` as canonical; the brief and IMPLEMENTATION_GUIDE FAQ want the step sequence included for loop iterations. | API_CONTRACT engine contract vs IMPLEMENTATION_GUIDE FAQ | First execution of a node in a run uses exactly `{run_id}:{node_id}`. A later loop iteration of the same node uses `{run_id}:{node_id}:s{sequence}`. Retries and resumes of one iteration reuse the persisted key (never an attempt number). | resolved |
| C7 | Cancelling a `waiting_approval` run must "close" its approval; DATA_MODEL lists approval statuses `pending/approved/rejected` only. | API_CONTRACT §5 vs DATA_MODEL | Add approval status `cancelled` (not actionable, never listed as pending). | resolved |
| C8 | Seeds carry `limits.timeout_seconds` (Good To Have). A wall-clock cap would fail `wf_expense_approval` runs whose human approval takes > 300 s. | seeds vs Case Study demo flow | `max_steps` always enforced. `timeout_seconds` enforcement is behind `relay.engine.enforce-timeout` (default false) and, when on, counts only time the run is not in `waiting_approval`. `max_ai_tokens` is enforced when > 0. | resolved |
| C9 | The definition shape carries `trigger.secret` in clear; EVALUATION_GUIDE requires webhook secrets never appear "in traces, console views, or error messages". | seed shape vs EVALUATION_GUIDE "Secret hygiene" | The secret is accepted in the seed shape on create/update, stripped before storage, kept as SHA-256 (constant-time compare) + hybrid-PQ envelope ciphertext, and rendered as `"secret": "********"` in responses. Run snapshots never contain it. | resolved |
| C10 | `http_request` non-2xx semantics are unspecified. The NL eval (`nl_002`) wants to branch on a non-200 status. | catalog vs nl_eval | 2xx–4xx responses complete the step with `{status, body}` so workflows can branch on them; 5xx / 429 / timeouts / connection errors are transient (retried, then the step fails). `notify` and `order_action` treat 4xx as permanent step failures (business-rule rejections). | resolved |
