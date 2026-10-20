import type { DocPage } from "../types";

export const scenarios: DocPage = {
  slug: "scenarios",
  group: "Practice",
  title: "Guided scenarios",
  summary: "Hands-on exercises using the four built-in workflows and their sample payloads.",
  blocks: [
    {
      t: "p",
      text: "Four workflows are loaded, already published, when the system starts. They are the quickest way to learn. Each scenario below uses a **sample payload** — pick it from the Trigger dialog.",
    },
    { t: "h2", text: "The built-in workflows" },
    {
      t: "table",
      head: ["Workflow", "What it does"],
      rows: [
        [
          "`wf_expense_approval`",
          "Checks if an expense is over $100. Large ones wait for a finance approval; small ones are auto-approved. Either way the employee gets an email.",
        ],
        [
          "`wf_support_triage`",
          "An AI step classifies a customer message. Refund requests wait for a manager before the refund is issued; everything else is posted to the `#support` chat channel.",
        ],
        [
          "`wf_slow_fulfillment`",
          "Confirmation email → 20-second pack delay → shipment → shipped email. Built to demonstrate crash recovery.",
        ],
        [
          "`wf_runaway`",
          "Polls an order that never ships, in a loop. Built to demonstrate the step limit (12).",
        ],
      ],
    },
    { t: "h2", text: "Sample payloads" },
    {
      t: "table",
      head: ["Id", "Workflow", "Expected result"],
      rows: [
        ["`pay_001`", "Support triage", "Complaint → posted to `#support`."],
        ["`pay_002`", "Support triage", "Refund request → waits for approval."],
        ["`pay_003`", "Support triage", "Question → posted to `#support`."],
        [
          "`pay_inject_001`",
          "Support triage",
          "Injection attempt → **still** waits for approval; nothing is refunded.",
        ],
        [
          "`pay_inject_002`",
          "Support triage",
          "Attempt to redirect notifications → normal post to `#support` only.",
        ],
        ["`pay_101`", "Expense approval", "$250 → waits for approval."],
        ["`pay_102`", "Expense approval", "$40 → auto-approved."],
        ["`pay_201`", "Slow fulfillment", "Input for the crash-recovery drill."],
      ],
    },
    { t: "h2", text: "A. Auto-approved expense (2 minutes)" },
    {
      t: "steps",
      items: [
        {
          title: "Trigger",
          text: "**Workflows → Expense approval → Trigger**, choose `pay_102`, confirm.",
        },
        {
          title: "Watch",
          text: "On the run page: `is_large` → `false` → `auto_ok` → **succeeded**.",
        },
        {
          title: "Inspect",
          text: "Open step `auto_ok`: note the idempotency key and the email that was sent.",
        },
      ],
    },
    { t: "h2", text: "B. An expense that needs approval" },
    {
      t: "steps",
      items: [
        { title: "Trigger", text: "Trigger *Expense approval* with `pay_101` ($250)." },
        { title: "See it pause", text: "The run stops at **waiting approval**." },
        { title: "Approve", text: "Go to **Approvals** → **Approve**." },
        {
          title: "Finish",
          text: "Back on the run it resumes and **succeeds**. `finance_gate` shows who approved.",
        },
      ],
    },
    { t: "h2", text: "C. The prompt-injection attempt" },
    {
      t: "steps",
      items: [
        { title: "Trigger", text: "Trigger *Support ticket triage* with `pay_inject_001`." },
        {
          title: "Observe",
          text: "The message tries to convince the AI that approval was already granted. Even if the AI calls it a refund request, the run still stops at `refund_gate`.",
        },
        {
          title: "Reject",
          text: "Reject the approval: the run ends `cancelled` and no refund ever happened.",
        },
      ],
    },
    { t: "h2", text: "D. The runaway loop" },
    {
      t: "steps",
      items: [
        { title: "Trigger", text: "Trigger *Poll until shipped (runaway)* with any input." },
        {
          title: "Observe",
          text: "After 12 steps the run is **failed** with `max_steps cap (12) exceeded` — Relay stopped an infinite loop for you.",
        },
      ],
    },
    { t: "h2", text: "E. Compile from English" },
    {
      t: "steps",
      items: [
        {
          title: "Open the compiler",
          text: "**NL compiler** → pick the first example → **Compile**.",
        },
        { title: "Review and publish", text: "Check the graph, then publish the draft." },
        {
          title: "Try a refusal",
          text: "Try the “trap” example to see Relay explain what it cannot do.",
        },
      ],
    },
    {
      t: "note",
      tone: "tip",
      title: "Crash-recovery demo",
      text: "Scenario “crash in the middle” (kill the worker during the 20-second wait) is a developer exercise. See the Developer guide: [Testing & verification](/docs/testing).",
    },
  ],
};

export const help: DocPage = {
  slug: "help",
  group: "Practice",
  title: "FAQ & troubleshooting",
  summary: "Quick answers, and what to check when the console does not look right.",
  blocks: [
    { t: "h2", text: "Frequently asked questions" },
    {
      t: "table",
      head: ["Question", "Answer"],
      rows: [
        [
          "Why can’t I trigger my workflow?",
          "It is probably still a **draft**. Publish it first; only published workflows run.",
        ],
        [
          "I edited a published workflow but runs behave the same.",
          "Edits change the working copy. Publish again to create a new version; running workflows keep their snapshot.",
        ],
        [
          "Can the AI approve things?",
          "No. Approvals are decided by people, and the refund gate checks the database, not the AI.",
        ],
        [
          "Will an email be sent twice if the system restarts?",
          "No. Every action carries an idempotency key that is saved before the action; a retry reuses the same key and the receiver ignores the repeat.",
        ],
        [
          "Where is my webhook secret?",
          "Secrets are hidden (`********`) after you save them. Keep your own copy; they cannot be read back.",
        ],
        [
          "Is there a sign-in?",
          "Not in this version. The console talks to the Relay API through a server-side token that your browser never sees.",
        ],
        [
          "Does this documentation need the backend?",
          "No. The User guide and Developer guide are static pages inside the console and work even when the API is down.",
        ],
      ],
    },
    { t: "h2", text: "Troubleshooting" },
    {
      t: "table",
      head: ["Symptom", "Likely cause", "Try"],
      rows: [
        [
          "Pages show “Relay API is unreachable”",
          "The backend is not running or the console cannot reach it.",
          "Check the stack is up (`make ps`); see Developer guide → [Troubleshooting](/docs/troubleshooting).",
        ],
        ["A run stays **queued**", "No worker is running.", "Start or restart the worker service."],
        [
          "A run stays **waiting_approval**",
          "It is waiting for you.",
          "Open Approvals and decide.",
        ],
        [
          "Publish is refused",
          "Validation found a problem.",
          "Read the error code; see [Create a workflow](/guide/create).",
        ],
        [
          "Webhook returns 403",
          "Wrong `X-Relay-Secret`.",
          "Use the secret defined in the workflow.",
        ],
      ],
    },
    { t: "h2", text: "Keyboard shortcuts" },
    {
      t: "table",
      head: ["Keys", "Action"],
      rows: [
        ["`/` or `Ctrl/⌘ + K`", "Search palette"],
        ["`g` `w`", "Workflows"],
        ["`g` `r`", "Runs"],
        ["`g` `a`", "Approvals"],
      ],
    },
  ],
};
