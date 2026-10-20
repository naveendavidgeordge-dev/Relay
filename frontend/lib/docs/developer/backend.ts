import type { DocPage } from "../types";

export const backend: DocPage = {
  slug: "backend",
  group: "Backend",
  title: "Backend & node types",
  summary: "How the Spring Boot service is organised, and the recipe for adding a node type.",
  blocks: [
    {
      t: "p",
      text: "`backend/` is one Spring Boot 3 / Java 21 application that runs in two modes. With the `api` profile it serves HTTP and never executes a node; with the `worker` profile (`WORKER_ONLY=true`) it has no HTTP server and drains the queue. Build and test with Gradle.",
    },
    {
      t: "code",
      lang: "bash",
      code: `cd backend
./gradlew bootRun --args='--spring.profiles.active=api'        # API on :8080
WORKER_ONLY=true ./gradlew bootRun --args='--spring.profiles.active=worker'
./gradlew bootJar                                              # runnable jar
./gradlew spotlessApply                                        # Google Java Format
./gradlew check                                                # tests + Checkstyle + SpotBugs`,
    },
    { t: "h2", text: "Coding standards" },
    {
      t: "ul",
      items: [
        "Java 21, **Google Java Format** (`spotlessApply`), records for DTOs, sealed interfaces for events and results.",
        "Constructor injection only. No Lombok in `domain`. Javadoc on public APIs.",
        "Checkstyle and SpotBugs run as part of `./gradlew check`.",
        "JSON over the wire is `snake_case`; null fields are omitted.",
      ],
    },
    { t: "h2", text: "Layers and where things live" },
    {
      t: "table",
      head: ["Concern", "Location"],
      rows: [
        ["Fixed HTTP contract (controllers)", "`com.relay.api`"],
        ["Publish validation (Chain of Responsibility)", "`com.relay.domain.validation`"],
        ["Queue, worker loop, run executor, template resolver", "`com.relay.engine`"],
        ["One executor per catalog node type", "`com.relay.nodes`"],
        ["Entities, repositories, JDBC queue adapter", "`com.relay.persistence`"],
        ["Auth, JWT, PQ crypto, secret vault, log masking", "`com.relay.security`"],
        ["Mock-world ACL and AI sidecar client", "`com.relay.integrations`"],
        ["Flyway migrations", "`src/main/resources/db/migration`"],
      ],
    },
    { t: "h2", text: "The node catalog is the boundary" },
    {
      t: "p",
      text: "`data/node_catalog.json` lists every allowed trigger and node type, their parameters, enums, which are templatable, and flags `side_effect` and `requires_approval`. The engine, the validator and the NL compiler must **never** emit a type outside it. The file is read-only; adding to it is a contract change that needs sign-off.",
    },
    { t: "h2", text: "Adding a node type" },
    {
      t: "steps",
      items: [
        {
          title: "Confirm it is in the catalog",
          text: "The type must already exist in `data/node_catalog.json`. If it does not, stop and raise it — do not edit `data/`.",
        },
        {
          title: "Implement the executor",
          text: "Create `backend/src/main/java/com/relay/nodes/<Type>NodeExecutor.java` implementing `NodeExecutor`. Copy param names, enums and defaults verbatim from the catalog into a params record.",
        },
        {
          title: "Register it",
          text: "Annotate it `@Component`. `NodeExecutorRegistry` collects every bean by `type()` and **fails startup** if a catalog type has no executor or an executor names an unknown type.",
        },
        {
          title: "Signal outcomes correctly",
          text: "Throw `TransientNodeException` for retryable failures (5xx, 429, timeouts) and `NodeFailureException` for permanent ones. Return `NodeResult.completed(...)`, `NodeResult.awaitApproval(...)` or `NodeResult.sleepUntil(...)`.",
        },
        {
          title: "Test it",
          text: "Unit test the executor (WireMock for HTTP), add a publish-validation case, update `docs/reqs/traceability.md` and `DESIGN/`, and add the type to the console’s `NodeTypeBadge`.",
        },
      ],
    },
    {
      t: "code",
      lang: "java",
      title: "Shape of an executor (modelled on DelayNodeExecutor)",
      code: `@Component
public class DelayNodeExecutor implements NodeExecutor {

  private final Clock clock;

  public DelayNodeExecutor(Clock clock) {         // constructor injection only
    this.clock = clock;
  }

  @Override
  public String type() {
    return "delay";                               // must equal a type in node_catalog.json
  }

  @Override
  public NodeResult execute(NodeExecutionContext ctx) {
    if (!ctx.param("seconds").isNumber()) {       // params arrive template-resolved
      throw new NodeFailureException(ErrorCode.INVALID_PARAM_VALUE, "seconds must be a number");
    }
    Instant resumeAt = ctx.stepStartedAt().plusMillis(Math.round(ctx.param("seconds").asDouble() * 1000));
    if (!resumeAt.isAfter(clock.instant())) {
      return NodeResult.completed(JsonNodeFactory.instance.objectNode());
    }
    return NodeResult.sleepUntil(resumeAt);       // never sleep a thread
  }
}
// Side-effect nodes also send ctx.idempotencyKey() on every mutating call, unchanged on retry.`,
    },
    {
      t: "note",
      tone: "info",
      title: "Project skill",
      text: "The repository ships a skill, `.claude/skills/relay/SKILL.md`, that walks through adding a node type, running the kill-and-resume drill and explaining a run from its trace.",
    },
  ],
};

export const engine: DocPage = {
  slug: "engine",
  group: "Backend",
  title: "Workflow engine",
  summary:
    "Run state machine, plan/act/record, idempotency, the approval gate, templates and AI nodes.",
  blocks: [
    { t: "h2", text: "Run state machine" },
    {
      t: "p",
      text: "The six statuses are a fixed contract, implemented as the State pattern in `domain.run.RunStatus`. A database CHECK constraint rejects any other value.",
    },
    {
      t: "code",
      lang: "text",
      title: "Transitions",
      code: `queued            → running           worker claims the job
queued            → cancelled         CANCEL
queued            → failed            snapshot signature invalid
running           → waiting_approval  approval node
running           → succeeded         next is null
running           → failed            step failed / max_steps / token cap
running           → cancelled         CANCEL (between steps)
waiting_approval  → running           approve
waiting_approval  → cancelled         reject or cancel`,
    },
    { t: "h2", text: "Persist, then act, then persist" },
    {
      t: "p",
      text: "This is the heart of exactly-once behaviour. For every node the engine does three things in order:",
    },
    {
      t: "code",
      lang: "text",
      code: `plan   (txn, run row locked)  INSERT step(status=running, resolved_input, idempotency_key)
                              UPDATE runs SET steps_executed = steps_executed + 1
act    (no txn)               POST /email/send   Idempotency-Key: run_x:confirm   (retries reuse it)
record (txn, run row locked)  UPDATE step SET status=succeeded, output=…
                              UPDATE runs SET current_node_id = <next>`,
    },
    { t: "h2", text: "Idempotency keys" },
    {
      t: "table",
      head: ["Situation", "Key"],
      rows: [
        ["First execution of a side-effect node", "`{run_id}:{node_id}`"],
        ["A loop revisits the same node", "`{run_id}:{node_id}:s{sequence}`"],
        ["A transient retry of one execution", "The same key, read back from the step row"],
        ["Resume after a crash", "The same key, read back from the step row"],
      ],
    },
    {
      t: "note",
      tone: "warn",
      title: "Never include the attempt number",
      text: "The attempt number must never be part of the key — that is exactly what would break exactly-once on retry. Side-effect nodes are `notify`, `order_action` and non-GET `http_request`.",
    },
    { t: "h2", text: "What happens when a worker dies" },
    {
      t: "table",
      head: ["Worker dies…", "Persisted state", "On resume"],
      rows: [
        ["Before plan commits", "Nothing for this node", "Node planned again; same key derived."],
        ["After plan, before the call", "Step `running`", "Re-executed with the persisted key."],
        [
          "After the call landed, before record",
          "Step `running`",
          "Re-executed with the **same** key; the mock world replays, the ledger shows one execution and one replay.",
        ],
        [
          "After record",
          "Step `succeeded`, run advanced",
          "Step skipped; resume starts at `current_node_id`.",
        ],
        [
          "During a delay",
          "Step `waiting`, `resume_at`",
          "Job unlocked until `resume_at`; any worker continues on time.",
        ],
      ],
    },
    { t: "h2", text: "The queue and leases" },
    {
      t: "ul",
      items: [
        "A worker claims a job with `SELECT … FOR UPDATE SKIP LOCKED` and sets `locked_until = now() + lease` (database clock).",
        "A heartbeat extends the lease every lease/3. `kill -9` stops the heartbeat; `reclaimExpired()` frees the job at worker start-up and on every poll.",
        "With a stable `RELAY_WORKER_ID` a restarted worker releases its previous incarnation’s leases at once instead of waiting for expiry.",
        "The `queue_jobs` table has `UNIQUE (run_id)`, so a run has at most one job.",
        "Retries use Resilience4j with exponential random backoff, but only for transient failures — never for schema failures.",
      ],
    },
    { t: "h2", text: "The approval gate" },
    {
      t: "p",
      text: "`ApprovalGate.authorizing(runId, sequence)` runs in `RunExecutor.plan` **before** any node whose catalog entry has `requires_approval: true`. It is a query over `approvals` rows:",
    },
    {
      t: "code",
      lang: "sql",
      code: `SELECT * FROM approvals
 WHERE run_id = :run AND status = 'approved' AND step_sequence < :gated_sequence
-- then keep only rows whose ML-DSA-65 decision signature verifies`,
    },
    {
      t: "p",
      text: "No row means the step is recorded `failed` with `approval_required` and the executor is never invoked, so no request reaches the outside world. The gate must never be moved into a prompt, a flag or a node parameter. Only `ApprovalService` changes an approval’s status, and only through the approve/reject endpoints with the `APPROVER` role.",
    },
    { t: "h2", text: "Templates" },
    {
      t: "ul",
      items: [
        "`{{trigger.body.x}}`, `{{nodes.<id>.output.y}}`, array indexes as `.0`. Single-pass path lookups only — no expressions, no functions.",
        "The resolved value is never re-scanned, so text from a payload cannot inject a template.",
        "A missing path fails the step with `template_unresolvable`, naming the expression and parameter.",
        "`amount_usd` is not templatable by design.",
      ],
    },
    { t: "h2", text: "The AI node" },
    {
      t: "code",
      lang: "text",
      code: `resolve templates (engine) → AiNodeExecutor
  1. ask(prompt, schema)                    → AiModelPort → sidecar /classify → provider
  2. parse (JSON, or a fenced block)
  3. validate against output_schema (draft-07) in Java, using the signed snapshot
  4. invalid → ask again once with the validation errors appended   [repair, exactly once]
  5. valid   → completed(output, tokens of both attempts)
  6. still invalid → NodeFailureException(ai_schema_violation) — never retried`,
    },
    {
      t: "p",
      text: "Validation is the engine’s job; the sidecar’s own check is only a hint. Token counts are summed over the repair and added to `runs.ai_tokens_used` even when the step fails, and `limits.max_ai_tokens > 0` is enforced before each node. Each attempt is recorded in `attempts_json`.",
    },
    { t: "h2", text: "Limits, cancellation, caps" },
    {
      t: "ul",
      items: [
        "`steps_executed` is incremented in the same transaction that inserts the step row. The cap check runs *before* the increment, so with `max_steps = 12` exactly 12 nodes execute and the 13th attempt fails the run with `max_steps cap (12) exceeded`.",
        "`limits.timeout_seconds` is off by default (`relay.engine.enforce-timeout`) because a wall-clock cap would cancel runs legitimately waiting for a human.",
        "Cancellation is cooperative between steps: a mid-step run gets `cancel_requested` and stops before the next node.",
        "Each run snapshot is signed at trigger and verified before execution, so editing and republishing a workflow never affects a run in flight.",
      ],
    },
  ],
};

export const database: DocPage = {
  slug: "database",
  group: "Backend",
  title: "Database",
  summary: "Tables, constraints, migrations and why the schema looks the way it does.",
  blocks: [
    {
      t: "p",
      text: "The authoritative schema is the Flyway migrations in `backend/src/main/resources/db/migration`: `V1__init.sql`, `V2__queue.sql`, `V3__approvals.sql`, `V4__pgcrypto.sql`. Flyway applies them on API start-up. Never edit a migration that has been applied — add a new one.",
    },
    { t: "h2", text: "Tables" },
    {
      t: "table",
      head: ["Table", "Key points"],
      rows: [
        [
          "`workflows`",
          "Working copy in the seed shape **minus** `trigger.secret`; `published_version` points at the latest frozen version; `has_unpublished_changes`.",
        ],
        [
          "`workflow_versions`",
          "One row per publish, never updated (a DB trigger raises on UPDATE); ML-DSA-65 signature and key id.",
        ],
        [
          "`runs`",
          "`definition_snapshot_json` (+ signature), `status` CHECK-constrained to the six values, `current_node_id`, `steps_executed`, `ai_tokens_used`, `error_json`, `cancel_requested`, lease mirror.",
        ],
        [
          "`steps`",
          "One row per node execution; `UNIQUE (run_id, sequence)`; a partial unique index allows at most one `running`/`waiting` step per run; holds `idempotency_key`, `resolved_input_json`, `output_json`, tokens, `resume_at`, `attempts_json`.",
        ],
        [
          "`approvals`",
          "`pending / approved / rejected / cancelled`; CHECK: decided ⇒ `decided_by` and `decided_at`; `step_sequence` lets the gate require “earlier in the run”; ML-DSA decision signature.",
        ],
        [
          "`queue_jobs`",
          "`UNIQUE (run_id)`; `available_at`, `locked_by`, `locked_until`, `attempts`, `trace_id`; partial index `WHERE locked_by IS NULL`.",
        ],
        ["`schedules`", "`next_fire_at`, partial index `WHERE enabled`."],
        ["`audit_log`", "Append-only actor / action / entity log."],
        [
          "`crypto_keys`, `workflow_secrets`, `api_tokens`, `ai_credentials`",
          "Key ring, encrypted webhook secrets, hashed API tokens and provider credentials — see [Security](/docs/security).",
        ],
      ],
    },
    { t: "h2", text: "Why a snapshot column *and* a version table?" },
    {
      t: "p",
      text: "A snapshot on the run is the must-have guarantee: the run follows the exact definition it started with, with one column. The version table is the history; it also lets `PUT` edit a published workflow’s working copy without touching what is runnable.",
    },
    { t: "h2", text: "Working with it locally" },
    {
      t: "code",
      lang: "bash",
      code: `# open a psql shell in the Compose Postgres
docker exec -it relay-postgres psql -U relay -d relay

# useful queries
select id, status, steps_executed from runs order by created_at desc limit 10;
select sequence, node_id, status, idempotency_key from steps where run_id = 'run_…' order by sequence;
select * from queue_jobs;                       -- empty when nothing is waiting for a worker`,
    },
    {
      t: "note",
      tone: "tip",
      title: "Reset to a clean state",
      text: "`make down` keeps the database. To start completely fresh, remove the volume: `docker compose -f docker/docker-compose.yml down -v` (this deletes all runs and workflows; seeds are re-loaded on next start).",
    },
  ],
};
