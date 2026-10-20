-- V2: runs (with their definition snapshots), steps (the trace) and the durable queue.
-- DATA_MODEL.md "Run", "Step", "Queue"; correctness requirements in DESIGN/04.

CREATE TABLE runs (
    run_id                    VARCHAR(64)  PRIMARY KEY,
    workflow_id               VARCHAR(128) NOT NULL REFERENCES workflows (id),
    workflow_version          INTEGER      NOT NULL,
    -- The definition as it was at trigger time. Runs execute THIS, never the live workflow.
    definition_snapshot_json  JSONB        NOT NULL,
    snapshot_signature        BYTEA,
    snapshot_key_id           VARCHAR(64),
    status                    VARCHAR(24)  NOT NULL CHECK (status IN
                                  ('queued', 'running', 'waiting_approval', 'succeeded', 'failed', 'cancelled')),
    trigger_type              VARCHAR(16)  NOT NULL CHECK (trigger_type IN ('webhook', 'manual', 'schedule')),
    input_json                JSONB        NOT NULL,
    current_node_id           VARCHAR(128),
    steps_executed            INTEGER      NOT NULL DEFAULT 0,
    ai_tokens_used            INTEGER      NOT NULL DEFAULT 0,
    error_json                JSONB,
    cancel_requested          BOOLEAN      NOT NULL DEFAULT FALSE,
    cancelled_by              VARCHAR(128),
    trace_id                  VARCHAR(64),
    -- Lease of the worker currently advancing the run (mirrors queue_jobs for visibility).
    lease_owner               VARCHAR(128),
    lease_expires_at          TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at                TIMESTAMPTZ,
    finished_at               TIMESTAMPTZ,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_runs_workflow_created ON runs (workflow_id, created_at DESC);
CREATE INDEX idx_runs_status_created ON runs (status, created_at DESC);
CREATE INDEX idx_runs_created ON runs (created_at DESC);

-- One row per node execution (attempt group). Node ids repeat when a run loops; sequence does not.
CREATE TABLE steps (
    id                   BIGSERIAL    PRIMARY KEY,
    run_id               VARCHAR(64)  NOT NULL REFERENCES runs (run_id) ON DELETE CASCADE,
    node_id              VARCHAR(128) NOT NULL,
    node_type            VARCHAR(32)  NOT NULL,
    sequence             INTEGER      NOT NULL,
    status               VARCHAR(16)  NOT NULL CHECK (status IN
                             ('running', 'succeeded', 'failed', 'waiting', 'cancelled')),
    attempt              INTEGER      NOT NULL DEFAULT 1,
    resolved_input_json  JSONB,
    output_json          JSONB,
    tokens_prompt        INTEGER,
    tokens_completion    INTEGER,
    -- Persisted BEFORE the side effect fires; reused verbatim by retries and resumes.
    idempotency_key      VARCHAR(256),
    -- delay nodes: when the worker may continue past this step.
    resume_at            TIMESTAMPTZ,
    approval_id          VARCHAR(64),
    -- Per-attempt log (transient retries and AI repair attempts) for the trace.
    attempts_json        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    error_json           JSONB,
    started_at           TIMESTAMPTZ  NOT NULL,
    finished_at          TIMESTAMPTZ,
    duration_ms          BIGINT,
    CONSTRAINT uq_steps_run_sequence UNIQUE (run_id, sequence)
);

-- "Traces are append-mostly and read by the console; index steps by run_id, sequence."
-- (uq_steps_run_sequence provides the (run_id, sequence) b-tree.)
CREATE INDEX idx_steps_run_node ON steps (run_id, node_id);
-- At most one in-flight (running / waiting) step per run.
CREATE UNIQUE INDEX uq_steps_one_inflight ON steps (run_id) WHERE status IN ('running', 'waiting');

-- Durable queue: one job per run that has work to do. SELECT ... FOR UPDATE SKIP LOCKED.
CREATE TABLE queue_jobs (
    id            BIGSERIAL    PRIMARY KEY,
    run_id        VARCHAR(64)  NOT NULL REFERENCES runs (run_id) ON DELETE CASCADE,
    enqueued_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    available_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_by     VARCHAR(128),
    locked_until  TIMESTAMPTZ,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    trace_id      VARCHAR(64),
    payload_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- A run is advanced by at most one job, hence by at most one worker.
    CONSTRAINT uq_queue_jobs_run UNIQUE (run_id)
);

CREATE INDEX idx_queue_jobs_ready ON queue_jobs (available_at) WHERE locked_by IS NULL;
CREATE INDEX idx_queue_jobs_leases ON queue_jobs (locked_until) WHERE locked_by IS NOT NULL;
