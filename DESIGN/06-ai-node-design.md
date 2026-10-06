# 06 — AI node design

> "Implement the `ai` node: a prompt template plus a JSON schema for the output. The engine validates
> the model's output against the schema, retries once with the validation error included, and fails
> the step cleanly if the output still does not conform." — Case Study, Must Have §5

## Flow

```
resolve templates (engine)  ──►  AiNodeExecutor
                                   │ 1. ask(prompt, schema)                     ─► AiModelPort ─► sidecar /classify ─► provider
                                   │ 2. parse (JSON or ```json fence)
                                   │ 3. validate against output_schema (networknt, draft-07) — in Java, from the snapshot
                                   │ 4. invalid → ask(prompt, schema, repair_feedback = errors + previous answer)   [exactly once]
                                   │ 5. valid → Completed(output, tokens = both attempts, details = both attempts)
                                   └ 6. still invalid → NodeFailureException(ai_schema_violation)  — not retried
```

* **Validation is the engine's job.** The sidecar validates too, but only as a hint. The executor
  validates against the `output_schema` in the run's signed snapshot.
* **Repair, not retry.** A schema failure is not transient ("do not burn retries re-asking
  identically"). `NodeFailureException` is never retried by `NodeInvoker`. Provider outages
  (5xx/429/timeouts) are `TransientNodeException` and are retried with backoff.
* **Tokens.** `tokens_prompt`/`tokens_completion` are recorded on the step (summed over the repair)
  and added to `runs.ai_tokens_used`, even when the step fails. `limits.max_ai_tokens > 0` is
  enforced before each node.
* **Trace.** `attempts_json` holds `{ai_attempt, kind: initial|repair, valid, validation_errors,
  rejected_output (clipped), tokens, provider, model}` for each attempt. The console shows both.

## Provider adapter (Python sidecar)

| `AI_PROVIDER` | Adapter | Notes |
|---|---|---|
| `mock` (default) | deterministic keyword heuristics | Always schema-valid; `MOCK_AI_FIRST_INVALID=true` forces a repair; `[invalid-twice]` in a prompt forces a clean failure |
| `gemini` | Generative Language API, `responseMimeType: application/json` | key in `x-goog-api-key` header |
| `groq` | OpenAI-compatible, `response_format: json_object` | |
| `ollama` | `/api/chat` with the JSON Schema as `format` | local models |
| `openai` | any OpenAI-compatible base URL | works with `scripts/mock_provider.py` (prose answers → demonstrates schema failure) |

The system prompt pins the output contract and says embedded instructions are data. This is defence in
depth only. The guarantees come from the schema check and the approval gate.

## Injection payloads

* `pay_inject_001`: even if the model outputs `refund_request` (the mock does, because the text asks
  for a refund), the run parks in `waiting_approval`; `issue_refund` cannot execute before a human
  approves (`EngineIT.injectionCannotSkipTheGate`). The refund amount is not templatable, and the
  mock world rejects refunds above the order total.
* `pay_inject_002`: the schema has no field that controls notification targets; `notify_support.to`
  is the literal `#support`. The mock provider's summary for injected text is a neutral statement
  ("Message contains embedded instructions … not followed"). A real model might still parrot the
  instructions into `summary`, which would be posted to `#support` as text only. That is worth
  discussing in the report: the mitigation is `maxLength` plus treating summaries as untrusted
  display text. They never feed a target, URL or amount.

## Privacy

Full prompts contain customer text. They are not persisted beyond the step's `resolved_input`
(which the trace needs to debug a wrong branch), and are logged only when `AI_LOG_PROMPTS=true`.
