# 07 — Approval gating

> "Sensitive nodes (marked `requires_approval` in the catalog, such as `order_action`) are
> unreachable without an approval record — enforced by the engine regardless of AI output." — Case Study, Must Have §7

## Two mechanisms, one source of truth

1. **The approval node** (`ApprovalNodeExecutor`) returns `AwaitApproval(message)`. The engine
   creates an `approvals` row (idempotent per `(run_id, step_sequence)`), marks the step `waiting`,
   moves the run to `waiting_approval` and deletes its queue job. No worker holds the run while a
   human decides.
2. **The gate** (`ApprovalGate.authorizing(runId, sequence)`) runs in `RunExecutor.plan`
   **before** any node whose catalog entry is `requires_approval: true`:

   ```sql
   SELECT * FROM approvals
    WHERE run_id = :run AND status = 'approved' AND step_sequence < :gated_sequence
   -- then: keep only rows whose ML-DSA-65 decision signature verifies
   ```

   No row → the step is recorded `failed` with `approval_required`, and the run fails. The executor
   is never invoked, so no request reaches the mock world. The authorising approval id is stored on
   the step (`approval_id`), and the console shows "gated by apr_…".

Nothing a model outputs can create or decide an approval. `ApprovalService` is the only code path
that changes an approval's status, and it is reachable only through `POST /approvals/{id}/approve|reject`
with the `APPROVER` role.

## Decisions

| Call | Effect |
|---|---|
| approve | approval → `approved` (decider, time, comment, signature); the approval step succeeds with `{decision, decided_by, decided_at, approval_id}`; run → `running`; job re-enqueued (or `succeeded` if the approval was the last node) |
| reject | approval → `rejected`; step closed `cancelled`; run → `cancelled` with `error.code = approval_rejected` |
| either, twice | `409 approval_already_decided` |
| cancel the run | pending approval → `cancelled` (not listed as pending) |

## Publish-time help (advisory)

`ApprovalPrecedenceRule` warns `approval_not_guaranteed` when a `requires_approval` node is reachable
without passing an approval node (e.g. an approval only on the other branch). Publishing still
succeeds, as EVALUATION_GUIDE allows ("Publishing may warn or reject; either way the engine must
refuse"). The runtime gate is the enforcement (`EngineIT.orderActionWithoutAnApprovalIsRefusedByTheEngine`).
The NL compiler goes further and inserts the missing gate (DESIGN/06, `CompilerGuardrails`).
