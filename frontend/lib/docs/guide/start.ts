import type { DocPage } from "../types";

export const welcome: DocPage = {
  slug: "welcome",
  group: "Getting started",
  title: "What is Relay?",
  summary: "The 60-second picture: what Relay does, why it is safe, and the words you will see.",
  blocks: [
    {
      t: "p",
      text: "Relay is a **workflow engine**. You describe a business process as a small graph of steps — for example “when an expense arrives, if it is over $100 ask finance to approve it, then email the employee” — and Relay runs that process every time something triggers it.",
    },
    {
      t: "p",
      text: "Think of it as automation with a safety harness: it is crash-proof, it never repeats an action twice, AI answers are checked before they are trusted, and risky actions need a human to click **Approve**.",
    },
    { t: "h2", text: "Why you can trust it" },
    {
      t: "table",
      head: ["Promise", "What it means for you"],
      rows: [
        [
          "**It never forgets**",
          "Every step is saved before Relay moves on. If the server restarts mid-run, the run continues from where it stopped.",
        ],
        [
          "**It never does things twice**",
          "Emails, refunds and shipments carry a unique key. A retry or a crash recovery can never send a second email or a second refund.",
        ],
        [
          "**AI is checked, not trusted**",
          "An AI step must answer in a strict format. If the answer is malformed Relay asks once more, then fails the step cleanly.",
        ],
        [
          "**Humans stay in control**",
          "Refunds and replacements only happen after a person approves. No message, AI answer or setting can skip that.",
        ],
        [
          "**Nothing runs forever**",
          "Every workflow has a step limit. A loop that never ends is stopped and reported.",
        ],
        [
          "**You can see everything**",
          "Every run has a full trace: what went in, what came out, how long it took, how many retries.",
        ],
      ],
    },
    { t: "h2", text: "Words you will see" },
    {
      t: "table",
      head: ["Term", "Meaning", "Example"],
      rows: [
        ["**Workflow**", "A named process made of steps, stored as JSON.", "`wf_expense_approval`"],
        [
          "**Node**",
          "One step in a workflow. Each node has a type from a fixed list.",
          "`is_large` (a condition), `auto_ok` (an email)",
        ],
        [
          "**Trigger**",
          "What starts a workflow: a webhook call, a manual click, or a schedule.",
          "Webhook, Manual, Schedule",
        ],
        [
          "**Run**",
          "One execution of a workflow, with its own input and its own trace.",
          "`run_01j9zq…`",
        ],
        [
          "**Step**",
          "One node executed inside a run (a looping node appears several times).",
          "step 3 = `approved_notice`",
        ],
        [
          "**Approval**",
          "A pause point where a person must Approve or Reject.",
          "“Expense of $250 from dev1@…”",
        ],
        [
          "**Draft / Published**",
          "Drafts can be edited; only published workflows can be run.",
          "v1, v2…",
        ],
      ],
    },
    {
      t: "note",
      tone: "tip",
      title: "Where to start",
      text: "Read [Console tour](/guide/tour) to learn where everything is, then follow the [guided scenarios](/guide/scenarios) — the first one takes two minutes.",
    },
  ],
};

export const tour: DocPage = {
  slug: "tour",
  group: "Getting started",
  title: "Console tour",
  summary: "A page-by-page walk through the web console you are using right now.",
  blocks: [
    {
      t: "p",
      text: "The console lives at `http://localhost:3000`. The left sidebar switches between pages; the top bar has search and the light/dark toggle. This guide and the Developer guide live at the bottom of the sidebar.",
    },
    { t: "h2", text: "The pages" },
    {
      t: "table",
      head: ["Page", "What you see", "What you can do"],
      rows: [
        [
          "**Dashboard** `/`",
          "Four tiles — runs today (with a sparkline), success rate, p95 duration, pending approvals — plus a “Waiting for approval” strip and recent runs.",
          "Jump straight to any run or approval.",
        ],
        [
          "**Workflows** `/workflows`",
          "A table of workflows: name, status (draft / published / edited), trigger, version, run count, last run.",
          "Filter by status, open a workflow, start a **New workflow**.",
        ],
        [
          "**Workflow detail** `/workflows/{id}`",
          "A **Graph** tab (diagram), a **Definition** tab (JSON), version history with signature status and any validation problems.",
          "**Publish** (with confirmation) and **Trigger** (pick a sample payload or type JSON).",
        ],
        [
          "**New workflow** `/workflows/new`",
          "A JSON editor with live validation and a live diagram.",
          "Save as draft, or save and publish.",
        ],
        [
          "**Runs** `/runs`",
          "History of every run: id, workflow, status, trigger, steps used / limit, duration, start time.",
          "Filter by status, workflow and date range; click a row to open it.",
        ],
        [
          "**Run detail** `/runs/{id}`",
          "Status header, a step **Timeline**, a **Graph** tab with visited nodes, and a side panel with the trigger payload and run facts.",
          "**Cancel run**. The page refreshes itself every ~1.5 s until the run finishes.",
        ],
        [
          "**Approvals** `/approvals`",
          "Pending approvals as cards, and a history of decided ones (who, when, comment).",
          "**Approve** or **Reject**, with an optional comment.",
        ],
        [
          "**NL compiler** `/compiler`",
          "A description box with example buttons.",
          "Describe a process in English and get a draft workflow back.",
        ],
        [
          "**User guide / Developer guide** `/guide`, `/docs`",
          "The documentation you are reading.",
          "Browse topics through the tab list on the left of each page.",
        ],
      ],
    },
    { t: "h2", text: "Navigation helpers" },
    {
      t: "table",
      head: ["Shortcut", "Does"],
      rows: [
        [
          "`/` or `Ctrl/⌘ + K`",
          "Open the search palette (pages, workflows, or paste a `run_…` id).",
        ],
        ["`g` then `w`", "Go to Workflows."],
        ["`g` then `r`", "Go to Runs."],
        ["`g` then `a`", "Go to Approvals."],
      ],
    },
    {
      t: "note",
      tone: "info",
      title: "Light and dark",
      text: "The console starts in dark mode. Use the sun/moon button in the top bar to switch; your choice is remembered in the browser.",
    },
  ],
};
