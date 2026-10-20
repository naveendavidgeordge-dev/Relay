import type { DocPage } from "../types";

export const api: DocPage = {
  slug: "api",
  group: "Backend",
  title: "API reference",
  summary: "Every route, its role, the error shape and copy-paste examples.",
  blocks: [
    {
      t: "p",
      text: "Base URL `http://localhost:8080`. JSON is `snake_case`. Every platform route needs `Authorization: Bearer <token>`; `/hooks/{id}` needs only `X-Relay-Secret`. Every response carries `X-Trace-Id`. Routes marked ✱ are the fixed contract exercised by `scripts/smoke_test.py`.",
    },
    { t: "h2", text: "Workflows" },
    {
      t: "table",
      head: ["Method & path", "Role", "Notes"],
      rows: [
        [
          "✱ `GET /workflows[?status=draft|published]`",
          "any",
          "Array of workflows with `status`, `trigger_type`, `published_version`, `has_unpublished_changes`, `run_count`, `last_run_*`, `webhook_path`.",
        ],
        [
          "✱ `POST /workflows`",
          "builder",
          "Body is a definition in the seed shape. `201`. Shape errors → `400 invalid_definition`; duplicate id → `409 workflow_exists`. `trigger.secret` is stored encrypted and rendered `********`.",
        ],
        [
          "`GET /workflows/{id}`",
          "any",
          "Adds `definition`, `published_definition`, `versions[]` (with `signature_valid`), `validation`.",
        ],
        [
          "`PUT /workflows/{id}`",
          "builder",
          "Replaces the **working copy**. Published versions are immutable.",
        ],
        [
          "✱ `POST /workflows/{id}/publish`",
          "builder",
          "Validates and freezes version N+1 (signed). `422` with the first violation’s `code` and `details.violations[]`.",
        ],
        [
          "`POST /workflows/validate`",
          "builder",
          "Dry run → `{valid, errors[], warnings[]}` (used by the console editor).",
        ],
        [
          "✱ `POST /workflows/{id}/trigger`",
          "builder, operator",
          'Body `{"input": {…}}` → `202 {run_id, status: "queued", …}`. Unpublished → `409 workflow_not_published`.',
        ],
        [
          "`POST /workflows/compile[?persist=false]`",
          "builder",
          "NL compiler: `{description}` → workflow draft, or a `refusal`.",
        ],
        [
          "`GET|POST /workflows/compile/eval`",
          "builder",
          "Scores `data/nl_eval.jsonl`; stores nothing.",
        ],
      ],
    },
    { t: "h2", text: "Triggers, runs, approvals" },
    {
      t: "table",
      head: ["Method & path", "Role", "Notes"],
      rows: [
        [
          "✱ `POST /hooks/{id}`",
          "`X-Relay-Secret`",
          "Body becomes `{{trigger.body}}` → `202`. Missing secret `401 missing_webhook_secret`; wrong `403 invalid_webhook_secret`; unknown `404`; not webhook-triggered `409 trigger_type_mismatch`.",
        ],
        [
          "`GET /runs?workflow_id=&status=&from=&to=&limit=`",
          "any",
          "Newest first, without `steps`.",
        ],
        [
          "✱ `GET /runs/{runId}`",
          "any",
          "Run plus the full trace: `status`, `input`, `steps[]`, `approvals[]`, `signature_valid`, `definition_snapshot`, `error`, timing.",
        ],
        ["`POST /runs/{runId}/cancel`", "operator", "Terminal run → `409 run_already_terminal`."],
        [
          "✱ `GET /approvals?status=pending`",
          "any",
          "Array of `{id, run_id, workflow_id, node_id, message, status, decided_by, …}`.",
        ],
        [
          "✱ `POST /approvals/{id}/approve`",
          "approver",
          'Optional `{"comment": "…"}`. Twice → `409 approval_already_decided`.',
        ],
        [
          "`POST /approvals/{id}/reject`",
          "approver",
          "The run ends `cancelled` with `error.code = approval_rejected`.",
        ],
        ["`GET /catalog`, `GET /samples`", "any", "Node catalog and sample payloads."],
        [
          "`GET /actuator/health`, `/actuator/prometheus`",
          "public / auth",
          "Liveness/readiness and metrics.",
        ],
      ],
    },
    { t: "h2", text: "Step fields" },
    {
      t: "p",
      text: "Each entry of `steps[]` has: `sequence`, `node_id`, `type`, `status` (`running|succeeded|failed|waiting|cancelled`), `attempt`, `input` (resolved params), `output`, `error`, `started_at`, `finished_at`, `duration_ms`, `tokens_prompt`, `tokens_completion`, `idempotency_key`, `approval_id`, `resume_at`, `side_effect`, `requires_approval`, and `attempts[]` (retries, replays, AI repair attempts).",
    },
    { t: "h2", text: "Errors" },
    {
      t: "code",
      lang: "json",
      code: `{"error": {"message": "Node 'n1' has unknown type 'teleport'",
           "code": "invalid_node_type",
           "trace_id": "4bf9…",
           "details": {}}}`,
    },
    {
      t: "table",
      head: ["Case", "Status", "Code"],
      rows: [
        ["Missing / invalid platform token", "401", "`unauthorized`"],
        ["Token lacks the role", "403", "`forbidden`"],
        [
          "Unknown workflow / run / approval",
          "404",
          "`workflow_not_found` / `run_not_found` / `approval_not_found`",
        ],
        ["Malformed JSON or bad query param", "400", "`invalid_request`"],
        ["Publish validation failed", "422", "First violation’s code (see below)"],
        ["Triggering an unpublished workflow", "409", "`workflow_not_published`"],
        ["Deciding an approval twice", "409", "`approval_already_decided`"],
        ["Cancelling a finished run", "409", "`run_already_terminal`"],
      ],
    },
    {
      t: "p",
      text: "Publish codes: `invalid_node_type`, `missing_required_param`, `invalid_param_value`, `invalid_edge_target`, `invalid_edge`, `invalid_entry`, `empty_nodes`, `duplicate_node_ids`, `invalid_trigger`, `invalid_limits`, `invalid_template`. Warnings (do not block): `unknown_param`, `unreachable_node`, `approval_not_guaranteed`.",
    },
    { t: "h3", text: "Engine codes on failed steps and runs" },
    {
      t: "table",
      head: ["Code", "Retried?", "Meaning"],
      rows: [
        [
          "`upstream_unavailable`",
          "yes",
          "5xx / 429 / connection refused / open circuit / full bulkhead.",
        ],
        ["`node_timeout`", "yes", "The dependency did not answer in time."],
        ["`upstream_rejected`", "no", "A 4xx business rule, e.g. refund above total."],
        ["`template_unresolvable`", "no", "A `{{…}}` path is missing."],
        ["`condition_not_numeric`", "no", "`greater_than`/`less_than` on non-numbers."],
        ["`ai_schema_violation`", "no", "Invalid after the one repair attempt."],
        ["`ai_provider_error`", "no", "Provider rejected the request."],
        [
          "`approval_required`",
          "no",
          "Gated node with no approved, signed approval earlier in the run.",
        ],
        ["`snapshot_signature_invalid`", "—", "The snapshot was modified after trigger."],
      ],
    },
    { t: "h2", text: "Examples" },
    {
      t: "code",
      lang: "bash",
      code: `TOKEN=relay-demo-token
URL=http://localhost:8080

# list, create, publish, trigger
curl -s $URL/workflows -H "Authorization: Bearer $TOKEN"
curl -s -X POST $URL/workflows -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d @workflow.json
curl -s -X POST $URL/workflows/wf_hello/publish -H "Authorization: Bearer $TOKEN"
curl -s -X POST $URL/workflows/wf_hello/trigger -H "Authorization: Bearer $TOKEN" \\
  -H 'Content-Type: application/json' -d '{"input":{}}'

# webhook
curl -s -X POST $URL/hooks/wf_expense_approval -H 'X-Relay-Secret: whsec_expense_774' \\
  -H 'Content-Type: application/json' -d '{"employee_email":"dev1@example.com","amount_usd":250,"description":"Conference ticket"}'

# trace and approvals
curl -s $URL/runs/run_… -H "Authorization: Bearer $TOKEN"
curl -s "$URL/approvals?status=pending" -H "Authorization: Bearer $TOKEN"
curl -s -X POST $URL/approvals/apr_…/approve -H "Authorization: Bearer $TOKEN"`,
    },
  ],
};

export const security: DocPage = {
  slug: "security",
  group: "Backend",
  title: "Security",
  summary:
    "Threat model, authentication and roles, secrets handling and post-quantum cryptography.",
  blocks: [
    { t: "h2", text: "Threat model" },
    {
      t: "table",
      head: ["Threat", "Mitigation"],
      rows: [
        [
          "Prompt injection in trigger payloads",
          "Payloads are data: single-pass path lookups only. AI output is schema-validated. `order_action` is gated by an approval-row query in the engine. Notify targets come from the definition. `amount_usd` is not templatable.",
        ],
        [
          "Forged approval (row edited in the DB, or replayed onto another run)",
          "Decisions are signed with ML-DSA-65 over `{approval_id, run_id, node_id, step_sequence, status, decided_by, decided_at}`; the gate ignores approvals whose signature does not verify.",
        ],
        [
          "Tampered snapshot",
          "Snapshot signed at trigger; verified before execution and on read (`signature_valid`).",
        ],
        [
          "Webhook secret theft from a DB dump",
          "Never stored in clear: SHA-256 for comparison, a hybrid post-quantum envelope wrapped by pgcrypto for recovery.",
        ],
        [
          "“Store now, decrypt later”",
          "Envelope key derived from ML-KEM-768 **and** X25519; breaking it needs both.",
        ],
        [
          "Timing attacks on secrets / tokens",
          "`MessageDigest.isEqual` on digests; constant-time token comparison.",
        ],
        [
          "Secret leakage via logs, API or errors",
          "Secrets stripped from stored definitions and snapshots and rendered `********`; `SensitiveDataMasker` on every log value; error bodies never include inputs; prompts logged only with `AI_LOG_PROMPTS=true`.",
        ],
        [
          "SSRF through templated `http_request` URLs",
          "Only absolute http(s) URLs, no redirects, optional `RELAY_HTTP_ALLOWED_HOSTS` allow-list, order ids path-encoded.",
        ],
        [
          "Unauthorised API use",
          "Bearer demo token, RS256 JWT or hashed API tokens, with role method guards.",
        ],
      ],
    },
    { t: "h2", text: "Authentication and roles" },
    {
      t: "table",
      head: ["Mechanism", "When", "Roles"],
      rows: [
        [
          "`Authorization: Bearer $RELAY_DEMO_TOKEN`",
          "Demo, smoke test, the console’s server",
          "All three",
        ],
        ["RS256 JWT (`RELAY_JWT_PUBLIC_KEY`, issuer check)", "Real deployments", "`roles` claim"],
        ["Platform API token `rly_…`", "Automation (SHA-256 digest lookup)", "Stored roles"],
        ["`X-Relay-Secret`", "`POST /hooks/{id}` only", "n/a"],
      ],
    },
    {
      t: "table",
      head: ["Role", "Allowed"],
      rows: [
        ["`BUILDER`", "Create, update, publish, validate, compile workflows; trigger."],
        ["`OPERATOR`", "Trigger and cancel runs."],
        ["`APPROVER`", "Approve and reject."],
        ["any authenticated role", "All reads."],
      ],
    },
    { t: "h2", text: "Hybrid KEM envelope" },
    {
      t: "code",
      lang: "text",
      code: `recipient key = ML-KEM-768 keypair ‖ X25519 keypair
encapsulate:   ss_pq, ct_pq = ML-KEM-768.Encaps(pk_pq)
               eph = X25519 keygen; ss_ec = X25519(eph.sk, pk_ec)
combiner:      salt = SHA3-256(ct_pq ‖ eph.pk ‖ pk_ec)
               dek  = HKDF-SHA3-256(ss_pq ‖ ss_ec, salt, "relay/hybrid-kem/v1|" ‖ context)
seal:          AES-256-GCM(dek, random 96-bit nonce, aad = "relay/envelope/v1|" ‖ context ‖ key_id)`,
    },
    {
      t: "ul",
      items: [
        "The context (for example `webhook_secret:wf_support_triage`) enters both the key derivation and the AEAD, so an envelope copied to another row does not decrypt.",
        "The envelope is additionally wrapped with pgcrypto `pgp_sym_encrypt_bytea` under `RELAY_DB_COLUMN_KEY`.",
        "Private keys are stored AES-256-GCM-wrapped by `RELAY_MASTER_KEY`. `KeyRing.rotate(purpose)` retires the active key and creates a new one; retired keys stay readable.",
        "Published versions, run snapshots and approval decisions are signed with ML-DSA-65 over canonical JSON (keys sorted recursively).",
      ],
    },
    {
      t: "note",
      tone: "info",
      title: "Software crypto",
      text: "ML-KEM and ML-DSA come from BouncyCastle; keys are wrapped by an environment-supplied KEK, not an HSM or cloud KMS. `RELAY_PQ_PROVIDER=test` is deterministic and **insecure** — tests only.",
    },
    { t: "h2", text: "The console and the token" },
    {
      t: "p",
      text: "The browser never sees the platform token. Server components call the API directly with `RELAY_API_TOKEN`, and client components call `/api/relay/*`, a same-origin proxy that adds the token and only forwards `workflows`, `runs`, `approvals`, `catalog` and `samples` — never `/hooks`. See [Frontend](/docs/frontend).",
    },
  ],
};

export const sidecar: DocPage = {
  slug: "ai-sidecar",
  group: "Services",
  title: "AI sidecar",
  summary: "The FastAPI adapter that hides gemini, groq, ollama and the mock behind one endpoint.",
  blocks: [
    {
      t: "p",
      text: "`ai-sidecar/` is a small FastAPI service. The Java engine calls one endpoint, `POST /classify`, with a prompt and a JSON Schema; the sidecar forwards it to the configured provider and returns JSON plus token counts. The engine does **not** trust the answer — it re-validates it (see [Workflow engine](/docs/engine)).",
    },
    {
      t: "code",
      title: "ai-sidecar/",
      lang: "text",
      code: `app/main.py                FastAPI app, /classify and health
app/prompts.py             system prompt that pins the output contract
app/schema.py              request/response and JSON-Schema helpers
app/providers/base.py      provider interface
app/providers/mock.py      deterministic keyword heuristics (default)
app/providers/http_providers.py   gemini, groq, ollama, openai-compatible
tests/                     pytest + FastAPI TestClient
pyproject.toml             ruff, black, mypy (strict)`,
    },
    { t: "h2", text: "Providers" },
    {
      t: "table",
      head: ["`AI_PROVIDER`", "Adapter", "Notes"],
      rows: [
        [
          "`mock` (default)",
          "Deterministic keyword heuristics",
          "Always schema-valid, works offline. `MOCK_AI_FIRST_INVALID=true` forces a repair; `[invalid-twice]` in a prompt forces a clean failure.",
        ],
        [
          "`gemini`",
          "Generative Language API",
          "`responseMimeType: application/json`; key sent in a header.",
        ],
        ["`groq`", "OpenAI-compatible", "`response_format: json_object`."],
        ["`ollama`", "`/api/chat`", "JSON Schema passed as `format`; local models."],
        ["`openai`", "Any OpenAI-compatible base URL", "Works with `scripts/mock_provider.py`."],
      ],
    },
    { t: "h2", text: "Running it" },
    {
      t: "code",
      lang: "bash",
      code: `cd ai-sidecar
pip install -e ".[dev]"
python -m uvicorn app.main:app --port 8090 --reload      # or: make sidecar
pytest                                                  # or: make test-sidecar
ruff check app tests && mypy app                        # lint + strict types

# use a real model
AI_PROVIDER=gemini GEMINI_API_KEY=… make sidecar`,
    },
    {
      t: "note",
      tone: "warn",
      title: "Defence in depth, not the guarantee",
      text: "The system prompt tells the model that embedded instructions are data, but the guarantees come from schema validation and the approval gate. Never log full prompts unless `AI_LOG_PROMPTS=true`.",
    },
  ],
};
