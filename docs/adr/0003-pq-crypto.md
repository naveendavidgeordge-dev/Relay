# ADR-0003: Hybrid post-quantum cryptography for secrets and signatures

- **Status:** accepted
- **Context:** Relay stores webhook secrets, platform tokens and (optionally) provider credentials,
  and its correctness depends on approval records and run snapshots not being edited. Ciphertext in
  backups is long-lived, which is exactly the "store now, decrypt later" exposure.
- **Decision:**
  * Envelope encryption with a **hybrid KEM**: ML-KEM-768 (FIPS 203) + X25519, combined with
    HKDF-SHA3-256 (X-Wing-style salt binding both ciphertexts), sealing with AES-256-GCM. The
    envelope is additionally wrapped with pgcrypto `pgp_sym_encrypt_bytea` (separate key).
  * Lookups never decrypt: SHA-256 digests compared with `MessageDigest.isEqual`.
  * **ML-DSA-65** (FIPS 204) signatures over canonical JSON of published versions, run snapshots
    and approval decisions; the engine refuses tampered snapshots and ignores unsigned/forged
    approvals.
  * BouncyCastle (`bcprov-jdk18on`) behind `PqCryptoService`; `TestPqCryptoService` for fast tests.
  * Keys in `crypto_keys`, private halves wrapped by an environment KEK; one active key per purpose;
    rotation keeps retired keys for decrypt/verify.
- **Consequences:** Larger ciphertexts (~1.1 KB per ML-KEM encapsulation) and signatures (~3.3 KB) —
  negligible at Relay's volumes. Software keys, not an HSM (stated limitation). If either primitive
  falls, the hybrid still protects secrets.
