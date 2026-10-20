# 04 — Idempotency and exactly-once side effects

> "Stable idempotency keys. `{run_id}:{node_id}` per side-effect node. A retry after a timeout and a
> resume after a crash must both send the same key the first attempt sent." — DATA_MODEL

## Key scheme

| Situation | Key |
|---|---|
| First execution of a side-effect node in a run | `{run_id}:{node_id}` |
| A loop revisits the same node | `{run_id}:{node_id}:s{sequence}` |
| Transient retry of one execution | the same key (it is read back from the step row) |
| Resume after a crash | the same key (it is read back from the step row) |

The attempt number is never part of the key. Side-effect nodes are `notify`, `order_action` and
non-GET `http_request` (a GET is a read and carries no key). `IdempotencyKeys.forStep` is the only
place the key is derived, and it runs exactly once per step, when the intent row is written.

## The protocol: persist, act, persist

```
plan   (txn, run row locked)   INSERT step(status=running, resolved_input, idempotency_key)
                               UPDATE runs SET steps_executed = steps_executed + 1
act    (no txn)                POST /email/send  Idempotency-Key: run_x:confirm   (retries reuse it)
record (txn, run row locked)   UPDATE step SET status=succeeded, output=…
                               UPDATE runs SET current_node_id = <next>
```

## Crash windows

| Worker dies… | Persisted state | On resume |
|---|---|---|
| before plan commits | nothing for this node | node planned again, same key derived (no prior execution) |
| after plan, before the call | step `running` | re-executed with the persisted key → executes normally |
| after the call landed, before record | step `running` | re-executed with the **same** key → the mock world replays (`x-mockworld-replayed: true`); the ledger shows one execution and one replay |
| after record | step `succeeded`, run advanced | the step is skipped; resume starts at `current_node_id` |
| during a delay | step `waiting`, `resume_at` | job is unlocked until `resume_at`; any worker continues on time |

"On resume you cannot know which happened — so you re-send with the same key and let replay
detection resolve it." — DATA_MODEL. `EngineIT.CrashRecovery` simulates the third row by throwing
an `Error` right after the mock world answered: the trace shows `create_shipment` with
`attempt = 2`, both requests carry `run_x:create_shipment`, and the confirmation email is not re-sent.

## Resume algorithm (`RunExecutor.plan`)

1. Lock the run. Terminal or `waiting_approval` → done.
2. `cancel_requested` → cancel (DESIGN/03).
3. An in-flight step exists?
   * `waiting` delay: before `resume_at` → reschedule the job; otherwise complete it and advance.
   * `waiting` approval: only the approvals API resumes it.
   * `running`: a worker died mid-step → increment `attempt`, log `resumed_after_worker_loss`, and
     re-execute with the persisted resolved input and key.
4. Otherwise plan `current_node_id` (caps, templates, approval gate, key, intent row).

Completed steps are never re-run: resume reads only `current_node_id` and the in-flight step.

## Leases

A claimed job has `locked_until = now() + lease` (DB clock); a heartbeat extends it every lease/3.
`kill -9` stops the heartbeat; `reclaimExpired()` (worker startup and every poll) frees the job. If
`RELAY_WORKER_ID` is stable (compose sets `worker-1`), the restarted worker releases its previous
incarnation's leases at once instead of waiting for expiry. A worker whose heartbeat finds a lease
gone stops at the next step boundary (`JobOutcome.Abandon`); the run row lock serialises any overlap.

## Proof

* `scripts/duplication_check.py` after `docker/drill.sh` (kill during `pack_delay`).
* `EngineIT.CrashRecovery` (kill inside the side-effect window), `EngineIT.transientFailuresAreRetriedWithTheSameKey`, `IdempotencyKeysTest`, `JdbcWorkQueueIT`.
