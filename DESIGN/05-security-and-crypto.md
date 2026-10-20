# 05 — Security and post-quantum cryptography

## Threat model

| Threat | Mitigation |
|---|---|
| Prompt injection in trigger payloads (`pay_inject_001/002`) | Payloads are data: single-pass path lookups only (no re-scanning, no expressions); AI output is schema-validated; `order_action` is gated by an approval-row query in the engine; notify targets come from the definition; `amount_usd` is not templatable |
| Forged approval (row edited in the DB, or replayed onto another run) | Decisions are signed with ML-DSA-65 over `{approval_id, run_id, node_id, step_sequence, status, decided_by, decided_at}`; the gate ignores approvals whose signature does not verify |
| Tampered snapshot (definition edited after trigger) | Snapshot signed at trigger; verified before execution and on read (`signature_valid`) |
| Webhook secret theft from a DB dump / backup | Secret never stored in clear: SHA-256 for comparison, hybrid-PQ envelope wrapped by pgcrypto for recovery |
| "Store now, decrypt later" (harvested ciphertext broken by a future quantum computer) | Envelope key derived from ML-KEM-768 **and** X25519; breaking it needs both |
| Timing attacks on secrets/tokens | `MessageDigest.isEqual` on digests; demo token compared in constant time |
| Secret leakage via logs / API / errors | Secrets stripped from stored definitions and snapshots, rendered `********`; `SensitiveDataMasker` on every log value; error bodies never include inputs; prompts logged only with `AI_LOG_PROMPTS=true` |
| SSRF through templated `http_request` URLs | Only absolute http(s) URLs, no redirects, optional `RELAY_HTTP_ALLOWED_HOSTS` allowlist; order ids are path-encoded before reaching the mock world |
| Unauthorised API use | Bearer demo token / RS256 JWT / hashed API tokens; roles `BUILDER`, `OPERATOR`, `APPROVER` with method guards |

## Hybrid KEM envelope (application layer)

```
recipient key  = ML-KEM-768 keypair  ‖  X25519 keypair          (crypto_keys, purpose=kem)
encapsulate:     ss_pq, ct_pq = ML-KEM-768.Encaps(pk_pq)
                 eph = X25519 keygen; ss_ec = X25519(eph.sk, pk_ec)
combiner:        salt = SHA3-256(ct_pq ‖ eph.pk ‖ pk_ec)
                 dek  = HKDF-SHA3-256(ikm = ss_pq ‖ ss_ec, salt, info = "relay/hybrid-kem/v1|" ‖ context)
seal:            AES-256-GCM(dek, random 96-bit nonce, aad = "relay/envelope/v1|" ‖ context ‖ "|" ‖ key_id)
envelope       = "RLE1" ‖ frame(key_id, frame(ct_pq, eph.pk), nonce ‖ ciphertext ‖ tag)
```

* **KEM combiner.** X-Wing style: both shared secrets go into the KDF, and the salt binds both
  ciphertexts and the recipient's classical key, so a key derived from one compromised component is
  still unpredictable.
* **Context binding.** The context (e.g. `webhook_secret:wf_support_triage`) enters both the KDF and
  the AEAD, so an envelope copied to another row does not decrypt (`WebhookSecretStoreIT`).
* **Defence in depth.** The envelope is stored inside `pgp_sym_encrypt_bytea(…, RELAY_DB_COLUMN_KEY)`
  (pgcrypto). A database dump needs both the KEK-wrapped KEM key and the column key.

## Keys and rotation

* `crypto_keys` holds public keys in clear and private keys AES-256-GCM-wrapped by
  `RELAY_MASTER_KEY` (32 bytes, env/secret manager; AAD = key id).
* Exactly one `active` key per purpose (partial unique index). API and worker converge through
  `INSERT … ON CONFLICT DO NOTHING` + re-read.
* `KeyRing.rotate(purpose)` retires the active key and creates a new one. Retired keys stay
  readable, so old envelopes decrypt and old signatures verify; new data uses the new key.
  Re-wrapping old envelopes is a background job (documented, not automated).
* KEK rotation = re-wrap every `private_key_wrapped` under the new KEK (a single SQL pass in a
  maintenance window).

## Signatures (ML-DSA-65, FIPS 204)

Signed: published `workflow_versions`, run snapshots (at trigger), approval decisions. Payloads are
canonical JSON (keys sorted recursively) so a signature survives the `jsonb` round trip.

## Implementations

`PqCryptoService` has two implementations: `BouncyCastlePqCryptoService` (bcprov-jdk18on: ML-KEM,
ML-DSA, SHA3, HKDF; JDK: X25519, AES-GCM) for production, and `TestPqCryptoService` (deterministic
HMAC/AES, **not secure**) for fast engine tests. `PqCryptoServiceTest` runs the same round-trip and
tamper tests against both.

## Transport

* Optional nginx front door (`--profile tls`): TLS 1.3 only, no session tickets, HSTS. The hybrid
  key exchange `X25519MLKEM768` is listed first as soon as the image ships OpenSSL ≥ 3.5; until then
  X25519. TLS terminates at the edge; inside the compose network services speak HTTP.
  `RELAY_MTLS_ENABLED` is reserved for mTLS between backend, sidecar and mock world. It would need
  client certificates on `OutboundHttp`/WebClient, and the stock mock world (stdlib `http.server`) has no TLS listener.
* HPKE (RFC 9180) was considered for webhook payload encryption and for sidecar requests. It was not
  adopted because callers authenticate with `X-Relay-Secret` over TLS by contract, and the standardised
  HPKE suites have no ML-KEM KEM yet. The envelope above already uses an HPKE-like KEM-then-AEAD
  structure and can move to HPKE once a hybrid suite is registered.

## AuthN/AuthZ

| Mechanism | When | Roles |
|---|---|---|
| `Authorization: Bearer $RELAY_DEMO_TOKEN` | Must-Have demo, smoke test | all three |
| RS256 JWT (`RELAY_JWT_PUBLIC_KEY`, issuer check) | real deployments | `roles` claim |
| Platform API token `rly_…` (`api_tokens`, SHA-256 digest lookup, envelope copy) | automation | stored roles |
| `X-Relay-Secret` | `POST /hooks/{id}` only | n/a |

Method guards: builder → create/update/publish/validate/compile; builder or operator → trigger;
operator → cancel; approver → approve/reject; any authenticated role → reads.
