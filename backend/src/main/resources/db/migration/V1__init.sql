-- V1: workflow definitions and their immutable published versions.
-- DATA_MODEL.md "Workflow": id, name, description, status (draft/published), definition, created_at, updated_at.

CREATE TABLE workflows (
    id                       VARCHAR(128) PRIMARY KEY,
    name                     VARCHAR(256) NOT NULL,
    description              TEXT,
    status                   VARCHAR(16)  NOT NULL CHECK (status IN ('draft', 'published')),
    trigger_type             VARCHAR(32)  NOT NULL,
    -- The working copy in the seed shape. trigger.secret is stripped before storage
    -- (see V4 workflow_secrets and DESIGN/00 conflict C9).
    definition_json          JSONB        NOT NULL,
    -- Latest frozen version (workflow_versions.version); NULL until first publish.
    published_version        INTEGER,
    has_unpublished_changes  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_workflows_status ON workflows (status);

-- Every publish freezes the working copy here. Rows are never updated.
CREATE TABLE workflow_versions (
    id               BIGSERIAL    PRIMARY KEY,
    workflow_id      VARCHAR(128) NOT NULL REFERENCES workflows (id) ON DELETE CASCADE,
    version          INTEGER      NOT NULL,
    definition_json  JSONB        NOT NULL,
    -- ML-DSA-65 signature over the canonical JSON of definition_json.
    signature        BYTEA,
    signing_key_id   VARCHAR(64),
    published_by     VARCHAR(128) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_workflow_versions UNIQUE (workflow_id, version)
);

-- Immutability guard: published versions are frozen at the database level too.
CREATE OR REPLACE FUNCTION forbid_workflow_version_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'workflow_versions rows are immutable (workflow %, version %)', OLD.workflow_id, OLD.version;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_workflow_versions_immutable
    BEFORE UPDATE ON workflow_versions
    FOR EACH ROW EXECUTE FUNCTION forbid_workflow_version_update();
