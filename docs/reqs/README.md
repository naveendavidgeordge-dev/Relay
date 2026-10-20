# Reference Inventory (`docs/reqs/`)

These files are the **source of truth** for Relay. They are copied verbatim from the
capstone pack and must not be edited. If a hash below stops matching, someone changed a
contract file — investigate before writing code.

Verify with:

```bash
sha256sum docs/reqs/*.md data/* scripts/*
```

## Precedence (highest first)

1. `scripts/smoke_test.py`, `scripts/duplication_check.py`
2. `API_CONTRACT.md` → "Fixed Contract"
3. `data/node_catalog.json`, `data/seed_workflows.json`
4. `DATA_MODEL.md` → "Correctness Requirements"
5. `EVALUATION_GUIDE.md`
6. `Case_Study.md` and `IMPLEMENTATION_GUIDE.md` (narrative)

Observed conflicts are recorded in [`DESIGN/00-overview.md`](../../DESIGN/00-overview.md#reference-conflicts-and-resolutions).

## Requirement documents

| File | SHA-256 | Governs |
|---|---|---|
| `API_CONTRACT.md` | `f2813d7feee5f6efa0e957dda3da87792771382b364d356aa6e96ed3fbcf2024` | Fixed routes, definition shape, status set, `X-Relay-Secret`, error body, engine behaviour contract |
| `DATA_MODEL.md` | `6e6e235166ad4baeb20c2185cacbb148fd8b2099564d5523bda92f6cbff5401e` | Entities, queue durability, persist-then-ack, idempotency key scheme, crash window, loop accounting |
| `EVALUATION_GUIDE.md` | `cf08b3273cf76e32d89157653c575521ea65359c396fdb4399fdfb031c9865b4` | Smoke test, kill-and-resume drill procedure, failure drills, NL eval scoring, reviewer checks |
| `IMPLEMENTATION_GUIDE.md` | `9146b8416f02ae2ec80fe52192e55499605d10704577e9ff61b53a3f0ee8cba3` | Must-Have checklist, build order, FAQ (template semantics, delays, resume) |
| `Case_Study.md` | `c67b3dcd3b39b0f3b2bd58cbfc03f57008bb4b34d151d91a869ac69d4fb4e472` | Problem statement: Must Have / Good To Have / Stretch scope, demo flow, assessment weights. Identical to `/RELAY_PROBLEM_STATEMENT.md`. The brief referred to `Case_Study.pdf`; only this Markdown rendering is present in the pack. |
| `PACK_README.md` | `7e17e8dccf41c244913f97e61012de5b5eec25a3e54f5620dfb983db4770857b` | Original pack README: mock-world usage, failure injection, language-agnostic expectations |

## Fixtures (read-only, at repo root)

| File | SHA-256 | Governs |
|---|---|---|
| `data/node_catalog.json` | `f3207f09be62e0b5c99ad021933dd21b6065186bc583bdc010a23c555417f869` | The only legal node + trigger types, params, `side_effect`, `requires_approval` |
| `data/seed_workflows.json` | `0d4ceec6c25e8cbd113dc88b1daf2d04422ac080ee760c91e21631ede70b8b78` | Canonical definition shape; four workflows loaded as Published |
| `data/sample_payloads.jsonl` | `ef7dbc235cb77fb51424dc17e837420a596d5a8c9f6ed20ed9fdd2088254d8c5` | Demo/test trigger bodies incl. `pay_inject_001/002` |
| `data/nl_eval.jsonl` | `5cc123851838fcabaf5343948cf33eb39494569a2b1dd82407efddba8789ae3b` | NL compiler eval (12 buildable, 3 traps) |
| `scripts/smoke_test.py` | `3799716b6025d1f6bbf09ab2ac3ade4db46b05f38d1aae291dc6adcfc04705b6` | Contract + lifecycle check — must pass unmodified |
| `scripts/duplication_check.py` | `095a7207ad19cc2e7b27dae9ec0ed8155d93b61959f13fc45f0d32c4c199946e` | Exactly-once verifier over the mock-world ledger — must pass unmodified |
| `scripts/mock_world.py` | `d59bc617479efa5b5379fde444492df1c46ae85bdc80a5fe2984891dc7e73383` | Fake external world (email/chat/orders/refunds/shipments + ledger) |
| `scripts/mock_provider.py` | `10b0a614a8a080f7799ac107b4ed6704040f36c92323ee2068cba35443580eb6` | OpenAI-compatible mock LLM (HTTP-level fake) |
| `scripts/validate_pack.py` | `d051ced5ff14a523b2a555ad80e676fe9aa77a51fe3b26cc240d864ce2fb96e5` | Pack self-consistency check |

`docs/API_CONTRACT.md`, `docs/DATA_MODEL.md`, `docs/EVALUATION_GUIDE.md` and
`docs/IMPLEMENTATION_GUIDE.md` are byte-identical duplicates of the files above (left
where the pack put them).

See also [`traceability.md`](traceability.md) (requirement → test → implementation) and
[`step-log.md`](step-log.md) (per-step reports with the quoted reference lines; this
replaces commit messages because this build is done without git actions).
