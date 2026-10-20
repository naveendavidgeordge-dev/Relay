---
name: relay
description: Relay project workflows — add a new node type end to end, run the kill-and-resume drill with expected output, or explain a run by narrating its trace from GET /runs/{id}. Use when the user asks to extend the node catalog/executors, verify durability, or understand why a Relay run did what it did.
---

# Relay project skill

Read `CLAUDE.md` first: the fixed contract, the hard rules, and the precedence order between
reference documents.

## A. Add a new node type

The catalog is the contract boundary. A new type is a **contract change**, so get explicit sign-off
from the user before touching `data/node_catalog.json`, and open an ADR in `docs/adr/`.

1. **Catalog entry** (after sign-off): add `{type, description, side_effect, requires_approval,
   params{name: {type, required, enum?, templatable?}}, output}` to `data/node_catalog.json`, then
   update its SHA-256 in `docs/reqs/README.md` (`make hashes`).
2. **Executor:** create `backend/src/main/java/com/relay/nodes/<Type>NodeExecutor.java`:
   * `@Component`, `implements NodeExecutor`, `type()` returns the catalog string exactly;
   * read params by their verbatim catalog names from `ctx.param(..)` / `ctx.text(..)`;
   * side effects: send `ctx.idempotencyKey()` on every mutating call (throw if it is null);
   * outbound calls go through `OutboundHttp` (timeouts, circuit breaker) or an ACL client like
     `MockWorldClient`;
   * throw `TransientNodeException` for 5xx/429/timeouts, `NodeFailureException` for permanent
     errors; return `NodeResult.completed / awaitApproval / sleepUntil`.
   `NodeExecutorRegistry` fails startup if any catalog type lacks an executor (or vice versa).
3. **Validation:** catalog params are validated generically by `ParamsRule`; add type-specific
   semantic checks there if needed, with cases in `PublishValidatorTest`.
4. **Tests:** a WireMock executor test like `NotifyNodeExecutorTest` (assert the exact body and the
   `Idempotency-Key`), and an `EngineIT` scenario if it changes control flow.
5. **Console:** add icon/label to `NODE_META` in `frontend/components/NodeTypeBadge.tsx`.
6. **Compiler:** teach `HeuristicWorkflowCompiler` the phrasing if it should be compilable; the
   LLM compiler picks it up from the catalog automatically.
7. **Docs:** DESIGN/01 pattern table if relevant, `docs/api.md` step fields, `docs/reqs/traceability.md`.
8. Run `make test`.

## B. Run the kill-and-resume drill

Prerequisites: `make up` (compose stack running; worker container `relay-worker`).

```bash
make drill        # = bash docker/drill.sh, following EVALUATION_GUIDE §2 step for step
```

Expected output (abridged):

```
==> 1/5 reset the mock world ledger
==> 2/5 trigger wf_slow_fulfillment (payload pay_201)
run_id=run_01j…
==> 3/5 wait for the confirmation email in the ledger, then kill -9 the worker during pack_delay
run is at node: pack_delay (expected pack_delay)
SIGKILL sent to relay-worker at 10:02:15Z (during pack_delay)
==> 4/5 restart the worker
==> 4/5 wait for the run to finish
final status: succeeded
  #1 confirm          succeeded  attempt=1 key=run_01j…:confirm
  #2 pack_delay       succeeded  attempt=1 key=None
  #3 create_shipment  succeeded  attempt=1 key=run_01j…:create_shipment
  #4 shipped_notice   succeeded  attempt=1 key=run_01j…:shipped_notice
==> 5/5 duplication check
Ledger entries checked: 3 (executed: 3, replays absorbed: 0, rejected: 0)
  email.send: 2 executed
  shipment.create: 1 executed

PASS: every side effect executed exactly once.
```

If killed inside a step instead of the delay, expect that step with `attempt=2` and
`replays absorbed: 1`. That is the mechanism working, not a failure. Running locally without
Docker: `WORKER_PID=<pid> bash docker/drill.sh`, then restart the worker with `make worker`.

## C. Explain a run

1. Fetch the trace: `curl -s localhost:8080/runs/<run_id> -H "Authorization: Bearer relay-demo-token"`.
2. Narrate, in order:
   * **Why it ran:** `trigger_type`, `workflow_id` + `workflow_version` (the snapshot it executed, and
     `signature_valid`), and the key fields of `input`.
   * **Path taken:** walk `steps[]` by `sequence`. For each: `node_id` (`type`) → status, attempts,
     duration. For `condition` steps quote `input.left op input.right` and `output.result`, which
     names the branch. For `ai` steps quote the validated `output`, the tokens, and whether
     `attempts[]` shows a repair (`ai_attempt 2`). For side effects quote the `idempotency_key` and
     any `replayed: true` in `attempts[]`. For `approval` steps say who decided and when (from
     `approvals[]`). For gated steps name the authorising `approval_id`.
   * **Where it stopped:** `status`; if failed/cancelled quote `error.code` + `error.message`
     (e.g. `max_steps cap (12) exceeded`, `approval_required`, `ai_schema_violation`).
   * **Anything odd:** retries (`attempt > 1`), resumed-after-worker-loss events, a `waiting` step
     with `resume_at`, or `cancel_requested`.
3. Never print secrets. Webhook secrets are already redacted in API output.
