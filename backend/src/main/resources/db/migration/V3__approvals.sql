-- V3: approvals (the gate is a query over these rows), schedules (Good To Have) and the audit log.

CREATE TABLE approvals (
    id                  VARCHAR(64)  PRIMARY KEY,
    run_id              VARCHAR(64)  NOT NULL REFERENCES runs (run_id) ON DELETE CASCADE,
    node_id             VARCHAR(128) NOT NULL,
    -- Sequence of the approval step; the engine gate requires it to precede the gated step.
    step_sequence       INTEGER      NOT NULL,
    message             TEXT         NOT NULL,
    -- 'cancelled' = closed because the run was cancelled (DESIGN/00 conflict C7).
    status              VARCHAR(16)  NOT NULL CHECK (status IN ('pending', 'approved', 'rejected', 'cancelled')),
    decided_by          VARCHAR(128),
    decided_at          TIMESTAMPTZ,
    decision_comment    TEXT,
    -- ML-DSA-65 signature over the canonical decision record (tamper evidence for the gate).
    decision_signature  BYTEA,
    decision_key_id     VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_approvals_run_step UNIQUE (run_id, step_sequence),
    CONSTRAINT ck_approvals_decided CHECK (
        (status = 'pending' AND decided_at IS NULL)
        OR (status <> 'pending' AND decided_at IS NOT NULL AND decided_by IS NOT NULL))
);

CREATE INDEX idx_approvals_status_created ON approvals (status, created_at DESC);
CREATE INDEX idx_approvals_run ON approvals (run_id);

-- Good To Have: cron triggers. Firing a schedule creates a normal Run.
CREATE TABLE schedules (
    id             BIGSERIAL    PRIMARY KEY,
    workflow_id    VARCHAR(128) NOT NULL UNIQUE REFERENCES workflows (id) ON DELETE CASCADE,
    cron           VARCHAR(128) NOT NULL,
    next_fire_at   TIMESTAMPTZ  NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_fired_at  TIMESTAMPTZ,
    last_run_id    VARCHAR(64)
);

CREATE INDEX idx_schedules_due ON schedules (next_fire_at) WHERE enabled;

CREATE TABLE audit_log (
    id           BIGSERIAL    PRIMARY KEY,
    actor        VARCHAR(128) NOT NULL,
    action       VARCHAR(64)  NOT NULL,
    entity_type  VARCHAR(32)  NOT NULL,
    entity_id    VARCHAR(128) NOT NULL,
    at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    meta_json    JSONB        NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id, at DESC);
