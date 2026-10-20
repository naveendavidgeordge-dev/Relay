# Demo script (≤ 90 s)

Setup: 1920×1080, browser zoom 110 %, dark mode, `make up` done, `curl -X POST localhost:9210/admin/reset`.
Pause ~1 s before every click. Terminal only for the drill.

| t | Screen | Action | Say |
|---|---|---|---|
| 0:00 | Dashboard `/` | — | "Relay: durable workflows with AI decisions. Four seeds are published." |
| 0:06 | `/workflows/wf_support_triage` | Graph tab | "Webhook → AI classify → branch → approval → refund." |
| 0:12 | same | **Trigger** → sample `pay_001` → Trigger run | "A normal complaint." |
| 0:18 | `/runs/run_…` | StepTimeline opens the AI step | "Validated output, 120+30 tokens, branch to `#support`, notify carries idempotency key `run:notify_support`." |
| 0:30 | `/workflows/wf_support_triage` | Trigger `pay_inject_001` | "This payload tells the model to skip approval and refund $5000." |
| 0:36 | run page | — | "The model said refund_request — fine. The engine still parks the run at the approval gate. The refund node can't run without an approval row." |
| 0:44 | `/approvals` | Approve (confirm) | "Approve: the run resumes and the refund executes once, gated by this approval." |
| 0:52 | run page | — | "`gated by apr_…`, succeeded." |
| 0:56 | `/workflows/wf_runaway` | Trigger | "A poll loop that never ends…" |
| 1:02 | run page | — | "…stopped at `max_steps cap (12) exceeded`." |
| 1:08 | terminal | `make drill` | "Kill -9 the worker during the 20 s delay, restart, resume from the last step." |
| 1:22 | terminal | duplication_check PASS | "Ledger: every side effect exactly once." |
| 1:26 | `/compiler` (optional) | Compile `nl_trap_001` | "And the compiler refuses what the catalog can't express." |
