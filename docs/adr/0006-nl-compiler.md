# ADR-0006: NL compiler = proposer + deterministic referee

- **Status:** accepted
- **Context:** Good To Have: plain English → a valid Draft using only catalog types, or a refusal.
  The eval rewards structural correctness and punishes invented node types. A model is not always
  available (offline demos, CI).
- **Decision:** `WorkflowCompiler` strategies **propose**; `CompilerService` **referees**:
  1. refuse if any proposed type is outside the catalog (never repair by guessing);
  2. insert an `approval` before every `requires_approval` node reachable without one, and say so;
  3. run the same `PublishValidator` as the API;
  4. store a valid result as a Draft (secret never echoed).

  Strategies: `heuristic` (default; deterministic clause planner, with capability-gap detection for
  databases, CRMs, SMS, telephony, inbox watching, CMS, spreadsheets) and `llm` (catalog + rules +
  seed example in the prompt; one repair with validator errors; falls back to heuristic on provider
  failure). `POST /workflows/compile/eval` scores `data/nl_eval.jsonl` without persisting anything.
- **Consequences:** The guarantees do not depend on model quality. The heuristic planner is tuned to
  common phrasings and will miss unusual ones; misses show up in the eval, which is reported
  honestly (see `docs/runbook.md`).
