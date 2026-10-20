# 08 — Error taxonomy

Every API error has one shape (API_CONTRACT §7):

```json
{"error": {"message": "Node 'n1' has unknown type 'teleport'", "code": "invalid_node_type", "trace_id": "4bf9…", "details": {}}}
```

`ErrorCode` (backend `domain/error/ErrorCode.java`) is the single list. `RelayException` is a sealed
hierarchy (`NotFoundException`, `ConflictException`, `AuthException`, `BadRequestException`,
`DefinitionValidationException`) rendered by `GlobalExceptionHandler`. Unexpected exceptions become
`500 internal_error` carrying only the trace id. Stack traces, SQL and inputs are never returned.

## HTTP mapping (matches API_CONTRACT §7)

| Case | Status | Code |
|---|---|---|
| Missing/invalid platform token | 401 | `unauthorized` |
| Token lacks the role | 403 | `forbidden` |
| Missing webhook secret | 401 | `missing_webhook_secret` |
| Wrong webhook secret | 403 | `invalid_webhook_secret` |
| Unknown workflow / run / approval | 404 | `workflow_not_found` / `run_not_found` / `approval_not_found` |
| Malformed JSON / bad query param | 400 | `invalid_request` |
| Definition shape broken at create/update | 400 | `invalid_definition` |
| Publish validation failed | 422 | first violation's code (below); all in `details.violations` |
| Triggering an unpublished workflow | 409 | `workflow_not_published` |
| Webhook to a non-webhook workflow | 409 | `trigger_type_mismatch` |
| Duplicate workflow id | 409 | `workflow_exists` |
| Approving an already-decided approval | 409 | `approval_already_decided` |
| Cancelling a terminal run | 409 | `run_already_terminal` |

## Publish-validation codes (422)

`invalid_node_type`, `missing_required_param`, `invalid_param_value`, `invalid_edge_target`,
`invalid_edge`, `invalid_entry`, `empty_nodes`, `duplicate_node_ids`, `cyclic_entry` (warning by
default), `invalid_trigger`, `invalid_limits`, `invalid_template`. Warnings: `unknown_param`,
`unreachable_node`, `approval_not_guaranteed`.

## Engine codes (on `steps.error_json` and `runs.error_json`)

| Code | Retried? | Meaning |
|---|---|---|
| `upstream_unavailable` | yes | 5xx / 429 / connection refused / open circuit / full bulkhead |
| `node_timeout` | yes | the dependency did not answer within the timeout |
| `upstream_rejected` | no | 4xx business rule from the mock world (e.g. refund above total, already refunded) |
| `template_unresolvable` | no | a `{{…}}` path is missing; message names the expression and param |
| `condition_not_numeric` | no | `greater_than`/`less_than` on non-numbers |
| `ai_schema_violation` | no | invalid after the one repair attempt |
| `ai_provider_error` | no | provider rejected the request (bad key, bad request) |
| `approval_required` | no | gated node with no approved, signed approval earlier in the run |
| `approval_rejected` | – | run ended by a rejection |
| `max_steps_exceeded` | – | `max_steps cap (N) exceeded` (cap checked before each node) |
| `token_budget_exceeded` / `timeout_exceeded` | – | Good-To-Have caps |
| `snapshot_signature_invalid` | – | the snapshot was modified after trigger |
| `cancelled` | – | run cancelled (`details.cancelled_by`) |

A failed step records `node_id`, `attempt`, code, message and details; a failed run's
`error.message` (also exposed as `failure_reason`) names the failing node or the cap.
