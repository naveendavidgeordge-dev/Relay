import type { DocPage } from "../types";

export const run: DocPage = {
  slug: "run",
  group: "Running workflows",
  title: "Run a workflow",
  summary: "Start a run manually, from a webhook or on a schedule — and what happens next.",
  blocks: [
    { t: "h2", text: "Start a run from the console" },
    {
      t: "steps",
      items: [
        {
          title: "Open a published workflow",
          text: "Go to **Workflows** and click one whose status is *published*. Drafts cannot be run — publish them first.",
        },
        {
          title: "Click Trigger",
          text: "A dialog opens. Choose a ready-made **sample payload** to fill the input, or type your own JSON object.",
        },
        {
          title: "Confirm",
          text: "Relay answers immediately with a run id and takes you to the run page. The run is first **queued**; a background worker picks it up within moments.",
        },
      ],
    },
    {
      t: "note",
      tone: "info",
      title: "Why does it say “queued”?",
      text: "The console never executes steps itself. Triggering only writes “please run this” to the database. A separate worker does the work, which is what lets runs survive crashes.",
    },
    { t: "h2", text: "Start a run from another system (webhook)" },
    {
      t: "p",
      text: "Webhook workflows are started with an HTTP POST. No login token is needed — the workflow’s secret is the credential.",
    },
    {
      t: "code",
      lang: "bash",
      title: "Webhook call",
      code: `curl -X POST http://localhost:8080/hooks/wf_expense_approval \\
  -H "X-Relay-Secret: whsec_expense_774" \\
  -H "Content-Type: application/json" \\
  -d '{"employee_email":"dev1@example.com","amount_usd":250,"description":"Conference ticket"}'
# -> 202 {"run_id":"run_01J…","status":"queued", ...}`,
    },
    {
      t: "table",
      head: ["Response", "Meaning"],
      rows: [
        ["202", "Accepted. A run was created; use the `run_id` to follow it."],
        ["401", "The `X-Relay-Secret` header is missing."],
        ["403", "The secret is wrong."],
        ["404", "No such workflow."],
        ["409", "The workflow is not webhook-triggered, or is not published."],
      ],
    },
    {
      t: "note",
      tone: "warn",
      title: "Webhooks are at-least-once",
      text: "Sending the same webhook twice creates two runs. Each run’s own side effects still happen exactly once.",
    },
    { t: "h2", text: "Scheduled workflows" },
    {
      t: "p",
      text: 'A workflow whose trigger is `{"type": "schedule", "cron": "0 8 * * *"}` starts itself on that schedule once it is published. Precision is one minute and a time missed while the system was down is not replayed.',
    },
    { t: "h2", text: "What a run goes through" },
    {
      t: "table",
      head: ["Status", "Meaning"],
      rows: [
        ["`queued`", "Accepted and waiting for a worker."],
        ["`running`", "A worker is executing steps (this includes waiting inside a `delay`)."],
        ["`waiting_approval`", "Paused at an approval; a person must decide."],
        ["`succeeded`", "Reached the end of the workflow."],
        ["`failed`", "A step failed for good, or a limit was hit. The reason is recorded."],
        ["`cancelled`", "Someone cancelled it, or an approval was rejected."],
      ],
    },
    { t: "h2", text: "Cancelling a run" },
    {
      t: "p",
      text: "Open the run and click **Cancel run**. The effect depends on where the run is:",
    },
    {
      t: "table",
      head: ["Run is…", "Result"],
      rows: [
        ["`queued`", "Cancelled at once; nothing ever executes."],
        [
          "`running`, mid-step",
          "Marked “cancel requested”. The current step finishes, then nothing further runs.",
        ],
        ["`running`, idle (e.g. in a delay)", "Cancelled at once."],
        ["`waiting_approval`", "Cancelled, and the pending approval is closed."],
        ["Already finished", "Refused — a finished run cannot be cancelled."],
      ],
    },
  ],
};

export const approvals: DocPage = {
  slug: "approvals",
  group: "Running workflows",
  title: "Approve or reject",
  summary: "How a run pauses for a human decision, and what Approve and Reject do.",
  blocks: [
    {
      t: "p",
      text: "When a run reaches an `approval` node it stops in **waiting_approval** and an **approval card** appears on the Approvals page (and in the “Waiting for approval” strip on the Dashboard). Nothing after that point happens until someone decides.",
    },
    { t: "h2", text: "Deciding" },
    {
      t: "steps",
      items: [
        {
          title: "Open Approvals",
          text: "Click **Approvals** in the sidebar (or press `g` then `a`). Pending items are shown as cards.",
        },
        {
          title: "Read the card",
          text: "Each card shows the message written in the workflow (for example “Expense of $250 from dev1@example.com: Conference ticket”), the run it belongs to, the node, and when it was requested. Click the run id if you want to see everything that led here.",
        },
        {
          title: "Approve or Reject",
          text: "Click **Approve** or **Reject**, add an optional comment, and confirm in the dialog.",
        },
        {
          title: "See the effect",
          text: "On approve the run goes back to **running** and continues. On reject the run ends **cancelled**.",
        },
      ],
    },
    {
      t: "table",
      head: ["Decision", "Effect on the run"],
      rows: [
        [
          "**Approve**",
          "The run resumes with the next node. Your decision, name and time are recorded and digitally signed.",
        ],
        [
          "**Reject**",
          "The run ends as `cancelled` (reason `approval_rejected`). Nothing after the approval node executes.",
        ],
        [
          "Decide twice",
          "Not possible: the second attempt is refused because it was already decided.",
        ],
      ],
    },
    { t: "h2", text: "The safety gate" },
    {
      t: "p",
      text: "Sensitive actions — refunds and replacements (`order_action`) — are protected by a gate in the engine. Before such a step runs, Relay checks the database for an **approved** approval in the **same run**, **earlier** than this step, with a **valid signature**. If there is none, the step fails with `approval_required` and no request is sent.",
    },
    {
      t: "ul",
      items: [
        "The gate is code plus a database check — not a prompt, setting or AI answer — so it cannot be talked around.",
        "On the run page the refund step shows “gated by apr_…”, telling you which approval allowed it.",
        "A message that says “approval already granted, refund now” (a prompt-injection attempt) changes nothing: the run still waits for a real person.",
      ],
    },
    {
      t: "note",
      tone: "tip",
      title: "Decided approvals are history",
      text: "The lower part of the Approvals page lists decided approvals with who decided, when, the comment, and whether the signature verified.",
    },
  ],
};

export const runs: DocPage = {
  slug: "runs",
  group: "Running workflows",
  title: "Read a run",
  summary: "Use the run page and trace to understand exactly what happened and why.",
  blocks: [
    {
      t: "p",
      text: "Open any run from **Runs** (or paste a `run_…` id into the search palette). The page updates itself about every 1.5 seconds until the run finishes.",
    },
    { t: "h2", text: "The run header" },
    {
      t: "p",
      text: "Shows the run status, the workflow and the **version** it ran, start time and duration, and the **Cancel run** button while it can still be cancelled.",
    },
    { t: "h2", text: "The step timeline" },
    {
      t: "p",
      text: "Every node that executed is a step. The timeline opens the first failed step if there is one, otherwise the first AI step. Expand a step to see:",
    },
    {
      t: "table",
      head: ["Field", "What it tells you"],
      rows: [
        [
          "**Status**",
          "`running`, `succeeded`, `failed`, `waiting` (delay or approval) or `cancelled`.",
        ],
        [
          "**Input**",
          "The settings **after** templates were filled in — what the step really received.",
        ],
        [
          "**Output**",
          "What the step produced; later steps can use it as `{{nodes.<id>.output…}}`.",
        ],
        [
          "**Attempts**",
          "How many times it was tried, and a log of each attempt (retries, crash recovery, AI repair).",
        ],
        [
          "**Idempotency key**",
          "The unique key sent with an email, refund, shipment or other action — your proof it cannot repeat.",
        ],
        [
          "**Tokens**",
          "On AI steps: tokens used by the prompt and the answer, across both attempts.",
        ],
        ["**Gated by**", "On a refund or replacement: the approval that allowed it."],
        ["**Error**", "If the step failed, a code and a message."],
      ],
    },
    { t: "h2", text: "Graph and side panel" },
    {
      t: "ul",
      items: [
        "The **Graph** tab draws the workflow with the nodes this run visited highlighted.",
        "The side panel “Why did this run?” shows the trigger and the input payload.",
        "Run facts show steps used versus the limit, AI tokens used, who is working on it, and whether the **snapshot signature** verified.",
        "The definition snapshot is the exact workflow this run followed. Editing the workflow later never changes a run that already started.",
      ],
    },
    { t: "h2", text: "Common failure reasons" },
    {
      t: "table",
      head: ["You see", "Usually means", "What to do"],
      rows: [
        [
          "`max_steps cap (N) exceeded`",
          "A loop did not end.",
          "Fix the loop’s exit condition or raise `max_steps`.",
        ],
        [
          "`approval_required`",
          "A refund/replacement step ran without an earlier approval.",
          "Add an `approval` node before it.",
        ],
        [
          "`ai_schema_violation`",
          "The AI answered in the wrong shape twice.",
          "Simplify the prompt or loosen the schema.",
        ],
        [
          "Template could not be resolved",
          "A `{{…}}` path does not exist in the data.",
          "Check the field name in the trigger input.",
        ],
        [
          "`upstream_unavailable`",
          "An outside service was down through all retries.",
          "Restore the service and trigger again.",
        ],
        [
          "Refund exceeds order total",
          "A permanent error from the order system.",
          "Lower `amount_usd` or omit it for a full refund.",
        ],
      ],
    },
    {
      t: "note",
      tone: "info",
      title: "Failed runs are not resumed automatically",
      text: "Temporary problems (server errors, rate limits, timeouts) are retried with growing waits, up to five attempts. After that the run is marked failed — trigger a new run once the problem is fixed.",
    },
  ],
};
