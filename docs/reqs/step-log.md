# Step Log

Each execution step from the build plan gets an entry here. The brief asks for the satisfied
reference quote at the top of each step's commit message. This build is done **without git
actions** (owner's instruction), so the would-be commit message is recorded here instead, quote
first.

Legend: ✅ done · 🟡 code written, not yet executed (toolchain not installed on the build machine) · ⏳ pending

---

## Step 1 — Scaffold monorepo ✅

**Would-be commit:** `chore: scaffold relay monorepo, guardrails and reference inventory`

> "README with setup instructions, API documentation, architecture, and design decisions
> (especially: the run state machine and how exactly-once recovery works)."
> — `Case_Study.md`, Deliverables §2

> "Use any persistent store you prefer. Preserve workflow and payload IDs such as
> `wf_support_triage` and `pay_inject_001`, and the workflow-definition JSON shape"
> — `Case_Study.md`, Must Have §1

**Files:** `README.md` (pack README preserved as `docs/reqs/PACK_README.md`), `CLAUDE.md`,
`.env.example`, `Makefile`, `package.json` (npm workspaces), `DESIGN/00-overview.md` (incl.
reference-conflict register), `docs/reqs/README.md` (SHA-256 inventory), `docs/reqs/traceability.md`.

**Satisfies:** "What should be in the final README? … setup instructions and environment variables
(model provider config, mock world URL)" — `IMPLEMENTATION_GUIDE.md`, FAQ.

**Tests:** n/a (documentation). Hashes verified with `sha256sum` and recorded.

**Toolchain check on the build machine:** Node 24.21 / npm 11.19 present. JDK, Gradle, Python,
Docker and Postgres **not installed** — per the owner, code targets current versions and will be
installed/configured at the end. Java/Python/compose verification is therefore deferred and every
such step is marked 🟡 until it is executed.

---

## Steps 2–6 🟡 (code + tests written, not yet executed — no JDK/Gradle/Docker on build machine)

| Step | Quote satisfied | Implementation | Tests |
|---|---|---|---|
| 2 Skeleton + GET /workflows | "`GET /workflows` — lists workflows with at least `id` and `status`." (API_CONTRACT Fixed §2) | build.gradle.kts, application.yml (api/worker profiles), V1__init.sql, CatalogConfig, SeedLoader, WorkflowService, WorkflowController, GlobalExceptionHandler, SecurityConfig, TraceIdFilter, SensitiveDataMasker | WorkflowControllerTest, WorkflowApiIT |
| 3 Publish validation | "Publish-time validation must reject, with a distinct message per case…" (API_CONTRACT §1) | domain/validation (Chain of Responsibility, 12 rules) | PublishValidatorTest, TemplateSyntaxTest |
| 4 Data model + crypto | "Workflows, runs (with their snapshots), steps, and approvals survive a full process restart" (DATA_MODEL) | V2–V4 migrations, entities, repositories, RunStatus state machine, PQ crypto (ML-KEM-768+X25519, ML-DSA-65), KeyRing, JdbcWebhookSecretStore | RunStatusTest, PqCryptoServiceTest, SensitiveDataMaskerTest, PersistenceIT, WebhookSecretStoreIT |
| 5 Queue + worker | "a `queue_jobs` table with `SELECT ... FOR UPDATE SKIP LOCKED`…" (DATA_MODEL Queue) | WorkQueue/JdbcWorkQueue, AbstractWorkerLoop, QueueWorker, WorkerLifecycle, RunExecutor, NodeInvoker, TemplateResolver (step 7 code), ApprovalGate (step 9 engine side) | JdbcWorkQueueIT, AbstractWorkerLoopTest, IdempotencyKeysTest, TemplateResolverTest |
| 6 Executors | "Implement the deterministic node types…: `http_request`, `condition`, `delay`, and `notify`" (Case Study MH §4) | HttpRequest/Condition/Delay/Notify executors, NodeExecutorRegistry, OutboundHttp, MockWorldClient | ConditionNodeExecutorTest, DelayNodeExecutorTest, HttpRequestNodeExecutorTest, NotifyNodeExecutorTest |

**Checkpoint after step 6:** the owner approved the interim resolutions for C1 (Case_Study.md),
C2 (dispatch-time URL aliases → ADR-0005) and C3 (`cyclic_entry` = warning by default).

## Steps 7–17

| Step | Quote satisfied | Implementation | Tests | State |
|---|---|---|---|---|
| 7 Templates | "Path lookups … interpolated into strings. No expressions… An unresolvable path should fail the step with a clear error" (IMPLEMENTATION_GUIDE FAQ) | `engine/template/TemplateResolver` (written in step 5) | `TemplateResolverTest` | 🟡 |
| 8 Triggers end-to-end | "`POST /hooks/{workflowId}` — … wrong/missing secret rejected 401/403; returns a run id" (API_CONTRACT Fixed §2) | `TriggerService`, `TriggerController`, `RunQueryService`, `RunController`, views | `WorkflowApiIT.webhook…`, `EngineIT.DeterministicFlows` | 🟡 |
| 9 Approvals + gate | "a node whose catalog entry has `requires_approval: true` must not execute unless an `approval` node earlier in the same run was approved" (API_CONTRACT engine contract) | `ApprovalNodeExecutor`, `OrderActionNodeExecutor`, `ApprovalService`, `ApprovalController`, `ApprovalGate` + signed decisions | `EngineIT.Approvals` | 🟡 |
| 10 AI node + sidecar | "validated against the node's `output_schema`; one retry with the validation error appended; then the step fails" (API_CONTRACT engine contract) | `AiNodeExecutor`, `AiModelPort`, `AiSidecarClient`, `ai-sidecar/` (mock/gemini/groq/ollama/openai) | `AiNodeExecutorTest`, `EngineIT.Ai`, `ai-sidecar/tests` | 🟡 |
| 11 Idempotency + drill | "Side-effect calls carry stable idempotency keys; `scripts/duplication_check.py` passes after the drill." (checklist) | `IdempotencyKeys`, `docker/drill.sh` (EVALUATION_GUIDE §2 verbatim) | `IdempotencyKeysTest`, `EngineIT.transientFailures…` | 🟡 |
| 12 Crash resume | "For the node that may have been in flight when the worker died, re-execute it with the same idempotency key" (IMPLEMENTATION_GUIDE FAQ) | `RunExecutor.resumeInFlight`, lease reclaim, stable worker id | `EngineIT.CrashRecovery` (kill inside the side-effect window and during the delay) | 🟡 |
| 13 Cap + cancel | "when `limits.max_steps` is exceeded, the run ends `failed` with a reason that names the cap"; API_CONTRACT §5 | `RunExecutor.plan`, `CancelService` | `EngineIT.Caps`, `EngineIT.Cancellation` | 🟡 |
| 14 Console | "A simple console shows workflows, run history, a trace view, and pending approvals." (checklist) | `frontend/` (Next 14.2.35, Tailwind, Radix, lucide, framer-motion, cmdk, zod) | 33 Vitest/RTL tests | ✅ **executed:** `vitest` 33/33 pass, `tsc` clean, `next lint` clean, `prettier --check` clean, `next build` OK |
| 15 Docker + CI | brief §10 | `docker/*.Dockerfile`, `docker/docker-compose.yml` (+ TLS profile), `.github/workflows/ci.yml` | CI jobs incl. smoke + drill | 🟡 |
| 16 NL compiler | "turns a plain-English description into a valid Draft workflow using only catalog node types — and refuses, with an explanation" (Case Study Good To Have) | `application/compiler/*`, `CompilerController`, console `/compiler` | `NlCompilerEvalTest` | 🟡 |
| 17 Docs | "README with setup instructions, API documentation, architecture, and design decisions" (Case Study Deliverables) | `README.md`, `DESIGN/00–10`, `docs/api.md`, `docs/runbook.md` (report template), `docs/adr/0001–0006`, `docs/demo-script.md`, `.claude/skills/relay/SKILL.md`, `docs/reqs/traceability.md` | — | ✅ |
| 18 Final verification | smoke test + drill output in the report | — | — | ⏳ needs JDK 21, Docker, Python 3.11 |
