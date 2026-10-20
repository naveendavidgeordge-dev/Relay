-- V4: at-rest protection for secrets (DESIGN/05-security-and-crypto.md).
--
-- Layering for every secret column:
--   1. application envelope: hybrid ML-KEM-768 + X25519 -> HKDF-SHA3-256 -> AES-256-GCM
--      (store-now-decrypt-later resistant; keys live in crypto_keys, wrapped by the env KEK)
--   2. pgcrypto pgp_sym_encrypt around the envelope with RELAY_DB_COLUMN_KEY
--      (a stolen dump or replica is useless without a second, separately-held key)
-- Lookups never decrypt: they compare SHA-256 digests (pgcrypto digest()) in constant time.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Asymmetric keys. Private halves are AES-256-GCM wrapped by RELAY_MASTER_KEY (never stored clear).
CREATE TABLE crypto_keys (
    key_id               VARCHAR(64)  PRIMARY KEY,
    purpose              VARCHAR(16)  NOT NULL CHECK (purpose IN ('kem', 'signing')),
    algorithm            VARCHAR(64)  NOT NULL,
    public_key           BYTEA        NOT NULL,
    private_key_wrapped  BYTEA        NOT NULL,
    status               VARCHAR(16)  NOT NULL CHECK (status IN ('active', 'retired')),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    retired_at           TIMESTAMPTZ
);

-- Exactly one active key per purpose; rotation retires the old one (kept for decrypt/verify).
CREATE UNIQUE INDEX uq_crypto_keys_active ON crypto_keys (purpose) WHERE status = 'active';

-- Webhook secrets (stripped from definition JSON on create/update).
CREATE TABLE workflow_secrets (
    workflow_id      VARCHAR(128) PRIMARY KEY REFERENCES workflows (id) ON DELETE CASCADE,
    secret_sha256    BYTEA        NOT NULL,
    secret_envelope  BYTEA        NOT NULL,
    kem_key_id       VARCHAR(64)  NOT NULL REFERENCES crypto_keys (key_id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    rotated_at       TIMESTAMPTZ
);

-- Platform API tokens: hashed for lookup, encrypted for audit/rotation, never stored clear.
CREATE TABLE api_tokens (
    id               VARCHAR(64)  PRIMARY KEY,
    name             VARCHAR(128) NOT NULL,
    token_sha256     BYTEA        NOT NULL UNIQUE,
    token_envelope   BYTEA        NOT NULL,
    kem_key_id       VARCHAR(64)  NOT NULL REFERENCES crypto_keys (key_id),
    roles            VARCHAR(128) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ,
    revoked_at       TIMESTAMPTZ
);

-- AI provider credentials (optional; the sidecar falls back to its environment).
CREATE TABLE ai_credentials (
    provider              VARCHAR(32)  PRIMARY KEY,
    credential_envelope   BYTEA        NOT NULL,
    kem_key_id            VARCHAR(64)  NOT NULL REFERENCES crypto_keys (key_id),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
