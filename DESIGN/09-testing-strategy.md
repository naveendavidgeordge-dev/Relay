# 09 — Testing strategy

| Layer | Tooling | What it proves |
|---|---|---|
| Unit (pure) | JUnit 5, AssertJ | `PublishValidatorTest` (every seed valid; the four contract rejections; loops legal; all other rules), `TemplateSyntaxTest`, `TemplateResolverTest` (incl. no re-scan of payload text), `RunStatusTest` (transition table), `IdempotencyKeysTest`, `PqCryptoServiceTest` (ML-KEM/X25519/ML-DSA round trips + tamper), `SensitiveDataMaskerTest`, `AbstractWorkerLoopTest`, `NodeExecutorRegistryTest`, `ConditionNodeExecutorTest`, `DelayNodeExecutorTest`, `AiNodeExecutorTest` (repair once, fail cleanly, tokens), `NlCompilerEvalTest` (the whole NL eval offline) |
| Adapter (WireMock) | WireMock JUnit 5 | `HttpRequestNodeExecutorTest`, `NotifyNodeExecutorTest`: exact mock-world bodies, `Idempotency-Key` on every mutating call (never on GET), 5xx/429/timeout transient, 4xx permanent, URL aliases, SSRF guard |
| Slice | `@WebMvcTest` | `WorkflowControllerTest`: 401/403/422/400/201 bodies, role guards, uniform error shape |
| Persistence | `@DataJpaTest` + Testcontainers Postgres 16 + Flyway | `PersistenceIT` (schema = entities, CHECKs, unique/partial indexes, immutable versions), `JdbcWorkQueueIT` (SKIP LOCKED, leases, heartbeat, reclaim), `WebhookSecretStoreIT` (secret never clear, constant-time match, envelope round trip through pgcrypto, rotation, signatures survive restart) |
| Integration | `@SpringBootTest` + Testcontainers + WireMock + fake model | `WorkflowApiIT` (HTTP contract, seeds, secrets redacted, webhook 401/403/202, 404/409); `EngineIT` — auto-approve path, retries with one key, retries exhausted, template failure, approve/reject, **injection payload parks at the gate**, **gate bypass by construction refused**, **snapshot integrity (edit + republish while paused)**, AI branch + tokens, **repair visible in trace**, schema failure without retries, **max_steps on wf_runaway** (0 s and real 1 s delays), **cancel in every state**, **kill inside the side-effect window → same key on resume**, kill during the delay |
| Sidecar | pytest + FastAPI TestClient | Seed payload classifications, injection summary does not parrot, every sample schema-valid, repair switch, invalid schema → 400, fence parsing |
| Console | Vitest + RTL | `RunStatusBadge`, `StepTimeline` (default-open rule, tokens, idempotency chip, gated chip, toggling), `ApprovalCard` (confirm flow), `WorkflowCanvas` (branches, back edges), API client (token, contract shape, error mapping, drift detection), KPI math |
| Contract | `scripts/smoke_test.py`, `scripts/duplication_check.py` (unmodified) | Run in CI against the compose stack (`integration` job); the drill kills the worker with `SIGKILL` during `pack_delay` |

Integration tests are tagged `integration`; `./gradlew test -PskipIntegration` runs the rest
without Docker. Frontend: `npm --workspace frontend test`.

Where the brief lists coverage targets, here they are mapped:

| Brief item | Test |
|---|---|
| publish validation (all 4 rejection cases) | `PublishValidatorTest.ContractCases`, `WorkflowApiIT.theSmokeTestsThreeBrokenDefinitions…` |
| template resolution | `TemplateResolverTest` |
| schema enforcement + repair retry | `AiNodeExecutorTest`, `EngineIT.Ai` |
| idempotency key stability across retries/resume | `EngineIT.transientFailures…`, `EngineIT.CrashRecovery` |
| approval gate (with/without approval) | `EngineIT.Approvals` |
| step cap on wf_runaway | `EngineIT.Caps` |
| cancel semantics for all states | `EngineIT.Cancellation` |
| snapshot integrity | `EngineIT.snapshotIntegrityEditAndRepublishWhilePaused` |
| kill-and-resume | `EngineIT.CrashRecovery` + `docker/drill.sh` |
