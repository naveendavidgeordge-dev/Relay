# Relay API reference

Base URL `http://localhost:8080`. JSON is `snake_case`; null fields are omitted. Every platform route
needs `Authorization: Bearer <token>` (demo token `RELAY_DEMO_TOKEN`, an RS256 JWT, or an `rly_…`
API token). `/hooks/{id}` needs only `X-Relay-Secret`. Every response carries `X-Trace-Id`.

✱ = fixed contract (API_CONTRACT "Fixed Contract"; exercised by `scripts/smoke_test.py`). Unmarked
routes are Relay's own additions, documented here as the contract requires.

## Workflows

| Method & path | Role | Notes |
|---|---|---|
| ✱ `GET /workflows[?status=draft\|published]` | any | JSON **array**; each item has `id`, `status`, `name`, `trigger_type`, `published_version`, `has_unpublished_changes`, `run_count`, `last_run_at`, `last_run_status`, `webhook_path` |
| ✱ `POST /workflows` | builder | Body: a definition in the seed shape. `201` + `Location`. Shape errors → `400 invalid_definition`; duplicate id → `409 workflow_exists`. Catalog rules are checked at publish (the response carries `validation`). `trigger.secret` is stored encrypted and rendered `********` |
| `GET /workflows/{id}` | any | Adds `definition` (working copy), `published_definition`, `versions[]` (with `signature_valid`), `validation` |
| `PUT /workflows/{id}` | builder | Replaces the **working copy**; published versions are immutable. A published workflow stays runnable at its latest version with `has_unpublished_changes: true`. Sending `"secret": "********"` keeps the stored secret |
| ✱ `POST /workflows/{id}/publish` | builder | Validates the working copy and freezes version N+1 (ML-DSA-65 signed). `422` with the first violation's `code` and `details.violations[]`. Re-publishing an unchanged workflow is a no-op `200` |
| `POST /workflows/validate` | builder | Dry-run validation → `{valid, errors[], warnings[]}` (console editor) |
| ✱ `POST /workflows/{id}/trigger` | builder, operator | Body `{"input": {...}}` → `202 {"run_id", "status": "queued", "workflow_id", "workflow_version", "trigger_type"}`. Unpublished → `409 workflow_not_published` |
| `POST /workflows/compile[?persist=false]` | builder | Good To Have. `{"description": "..."}` → `{workflow (secret redacted), workflow_id (stored draft), validation, explanation, notes[], compiler}` or `{refusal: {reason, missing_capabilities[]}, compiler}` |
| `GET\|POST /workflows/compile/eval` | builder | Runs `data/nl_eval.jsonl` → `{total, correct, accuracy, compiler, cases[{id, expected, result, checks{}, explanation, node_types[]}]}`; stores nothing |

## Triggers

| Method & path | Auth | Notes |
|---|---|---|
| ✱ `POST /hooks/{id}` | `X-Relay-Secret` | Body becomes `{{trigger.body}}` → `202 {"run_id", …}`. Missing secret `401 missing_webhook_secret`; wrong `403 invalid_webhook_secret`; unknown id `404`; not webhook-triggered `409 trigger_type_mismatch`. The same POST twice creates two runs (webhooks are at-least-once); each run's side effects are exactly-once |

## Runs

| Method & path | Role | Notes |
|---|---|---|
| `GET /runs?workflow_id=&status=&from=&to=&limit=` | any | Newest first, no `steps`. `workflowId` is accepted as an alias. `from`/`to` are ISO-8601 instants |
| ✱ `GET /runs/{runId}` | any | `run_id`, `status`, `workflow_id`, `workflow_name`, `workflow_version`, `trigger_type`, `input`, `current_node_id`, `steps_executed`, `max_steps`, `ai_tokens_used`, `error{code,message,details}`, `failure_reason`, `cancel_requested`, timing, `trace_id`, `lease_owner`, `signature_valid`, `definition_snapshot`, `approvals[]`, **`steps[]`** |
| `POST /runs/{runId}/cancel` | operator | queued / waiting_approval / idle-running → `cancelled` now; running with a worker → `cancel_requested: true`, stops before the next node; terminal → `409 run_already_terminal` |

Step fields: `sequence`, `node_id`, `type`, `status` (`running|succeeded|failed|waiting|cancelled`),
`attempt`, `input` (resolved params), `output`, `error`, `started_at`, `finished_at`, `duration_ms`,
`tokens_prompt`, `tokens_completion` (ai), `idempotency_key` (side effects), `approval_id` (created
by an approval step or authorising a gated step), `resume_at` (delay), `side_effect`,
`requires_approval`, `attempts[]` (retries, replays, AI repair attempts).

## Approvals

| Method & path | Role | Notes |
|---|---|---|
| ✱ `GET /approvals?status=pending` | any | JSON **array** of `{id, run_id, workflow_id, node_id, step_sequence, message, status, created_at, decided_by, decided_at, comment, signature_valid}`. Without `status`, all approvals newest first |
| `GET /approvals/{id}` | any | One approval |
| ✱ `POST /approvals/{id}/approve` | approver | Optional `{"comment": "..."}`. The run resumes. Decided twice → `409 approval_already_decided` |
| `POST /approvals/{id}/reject` | approver | The run ends `cancelled` (`error.code = approval_rejected`) |

## Reference data

| `GET /catalog` | the node catalog as loaded |
|---|---|
| `GET /samples[?workflow=]`, `GET /samples/{id}` | `data/sample_payloads.jsonl` |
| `GET /actuator/health` | public liveness/readiness |
| `GET /actuator/prometheus` | metrics (authenticated) |

## Errors

`{"error": {"message", "code", "trace_id", "details"?}}` — see DESIGN/08 for every code and status.

## Examples

```bash
TOKEN=relay-demo-token
curl -s -X POST localhost:8080/hooks/wf_support_triage -H 'X-Relay-Secret: whsec_triage_501' \
  -H 'Content-Type: application/json' \
  -d '{"order_id":"ord_2001","customer_email":"maya@example.com","message":"My earbuds arrived with a cracked charging case."}'
curl -s localhost:8080/runs/run_… -H "Authorization: Bearer $TOKEN" | jq '.status, .steps[] | {node_id, status, tokens_prompt}'
curl -s -X POST localhost:8080/workflows/compile -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"description":"When a webhook receives a new order, check if the total amount is over 100 dollars. If it is, ask a manager for approval and then email the customer a confirmation. Otherwise email the confirmation right away."}'
```
