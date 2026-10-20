import type { DocPage } from "../types";

export const concepts: DocPage = {
  slug: "workflows",
  group: "Building workflows",
  title: "Understanding workflows",
  summary: "Nodes, arrows, triggers and templates — everything a workflow is made of.",
  blocks: [
    {
      t: "p",
      text: "A workflow is a small graph. It has a name, a **trigger** (what starts it), an **entry** node (where it begins) and a list of **nodes**. Each node points to the next one with `next`; a `condition` node has two exits, `on_true` and `on_false`. A `next` of `null` means “the workflow ends here”.",
    },
    {
      t: "code",
      title: "A minimal workflow (JSON)",
      lang: "json",
      code: `{
  "id": "wf_hello",
  "name": "Hello world",
  "trigger": { "type": "manual" },
  "entry": "hello",
  "limits": { "max_steps": 5, "timeout_seconds": 60, "max_ai_tokens": 0 },
  "nodes": [
    {
      "id": "hello",
      "type": "notify",
      "params": { "channel": "chat", "to": "#general", "message": "Hello from {{trigger.body.name}}" },
      "next": null
    }
  ]
}`,
    },
    { t: "h2", text: "The seven node types" },
    {
      t: "p",
      text: "These are the only building blocks Relay allows. If a workflow uses anything else it is rejected when you publish.",
    },
    {
      t: "table",
      head: ["Type", "What it does", "Main settings", "Needs approval?"],
      rows: [
        [
          "`http_request`",
          "Calls a web address. A 404 or 400 is recorded (not a failure) so a later condition can branch on it; 5xx, 429 and timeouts are retried.",
          "`method`, `url`, optional `headers`, `body`",
          "No",
        ],
        [
          "`condition`",
          "Compares two values and picks a branch. `greater_than` / `less_than` compare as numbers.",
          "`left`, `op`, `right`",
          "No",
        ],
        [
          "`delay`",
          "Waits. The wait is stored, so a restart during a delay does not lose or restart it.",
          "`seconds`",
          "No",
        ],
        [
          "`notify`",
          "Sends an email or a chat message.",
          "`channel` (email/chat), `to`, `subject`, `message`",
          "No",
        ],
        [
          "`ai`",
          "Asks an AI model a question and forces the answer into the JSON format you define.",
          "`prompt`, `output_schema`",
          "No",
        ],
        [
          "`approval`",
          "Pauses the run until a person approves or rejects.",
          "`message` (shown to the approver)",
          "No",
        ],
        [
          "`order_action`",
          "Refund or replacement on an order.",
          "`action`, `order_id`, optional `amount_usd`",
          "**Yes** — needs an earlier approved approval in the same run",
        ],
      ],
    },
    {
      t: "p",
      text: "The `condition` operators are `equals`, `not_equals`, `greater_than`, `less_than` and `contains`.",
    },
    {
      t: "note",
      tone: "warn",
      title: "Refund amounts are fixed",
      text: "`amount_usd` on an `order_action` cannot use a template. It is written into the workflow so no incoming message or AI answer can change how much is refunded. Leave it out for a full refund.",
    },
    { t: "h2", text: "Triggers" },
    {
      t: "table",
      head: ["Trigger", "How it starts a run", "Notes"],
      rows: [
        [
          "`webhook`",
          "An external system sends `POST /hooks/{workflow id}` with the header `X-Relay-Secret`.",
          "The secret is the login. Missing → 401, wrong → 403. The request body becomes `{{trigger.body}}`.",
        ],
        [
          "`manual`",
          "You click **Trigger** in the console (or call the trigger API).",
          "The input you provide becomes `{{trigger.body}}`.",
        ],
        [
          "`schedule`",
          "A 5-field cron expression, for example `*/5 * * * *`.",
          "One-minute precision. A time that was missed is not replayed.",
        ],
      ],
    },
    { t: "h2", text: "Templates: passing data between steps" },
    {
      t: "p",
      text: "Any setting marked as templatable can contain placeholders that Relay fills in just before the step runs.",
    },
    {
      t: "table",
      head: ["Write this", "Gets replaced by"],
      rows: [
        ["`{{trigger.body.customer_email}}`", "A field from the data that started the run."],
        [
          "`{{nodes.classify.output.category}}`",
          "A field from an earlier step’s output (`classify` is that node’s id).",
        ],
        ["`{{nodes.create_shipment.output.body.shipment_id}}`", "A nested field."],
        ["`{{trigger.body.items.0.sku}}`", "An array item, counted from 0."],
      ],
    },
    {
      t: "ul",
      items: [
        "Only these lookups exist — no maths, no functions.",
        "If a path does not exist the **step fails with a clear message**. Relay never silently inserts an empty value.",
        "Text that arrives in a payload is never re-read as a template, so a message that contains `{{…}}` cannot trick Relay.",
      ],
    },
    { t: "h2", text: "Limits" },
    {
      t: "table",
      head: ["Limit", "What happens"],
      rows: [
        [
          "`max_steps`",
          "Checked before each step. Going over ends the run as **failed** with the reason `max_steps cap (N) exceeded`. This is what stops infinite loops.",
        ],
        [
          "`max_ai_tokens`",
          "If above 0, a run that has used more tokens than this stops before its next step.",
        ],
        [
          "`timeout_seconds`",
          "Present in every definition but switched off by default, because a wall-clock cap would kill runs that are legitimately waiting for a person.",
        ],
      ],
    },
    {
      t: "h2",
      text: "Draft, published and versions",
    },
    {
      t: "ul",
      items: [
        "**Draft** — saved, editable, cannot be run.",
        "**Published** — validated against the catalog and frozen as a numbered version (v1, v2…). Only published workflows can be triggered.",
        "Editing a published workflow changes its **working copy**. The workflow keeps running its last published version until you publish again, and runs already in progress are never affected.",
        "Webhook secrets you type into a workflow are encrypted on save and shown as `********` forever after.",
      ],
    },
  ],
};

export const create: DocPage = {
  slug: "create",
  group: "Building workflows",
  title: "Create a workflow",
  summary: "Two ways to build one: write the JSON in the editor, or describe it in plain English.",
  blocks: [
    { t: "h2", text: "Option 1 — the JSON editor" },
    {
      t: "steps",
      items: [
        {
          title: "Open the editor",
          text: "Go to **Workflows → New workflow** (`/workflows/new`). It opens with a working “Hello world” template.",
        },
        {
          title: "Edit the definition",
          text: "Change the `id`, `name`, `trigger` and `nodes`. Node ids must be unique and every `next` / `on_true` / `on_false` must point at a node that exists (or be `null`).",
        },
        {
          title: "Watch the live checks",
          text: "As you type, Relay validates the definition (after a short pause) and draws the diagram. Errors appear with a code such as `missing_required_param` and a plain message.",
        },
        {
          title: "Save",
          text: "**Save draft** keeps it editable. **Save & publish** validates fully and freezes version 1 so it can be run immediately.",
        },
      ],
    },
    { t: "h3", text: "A branching example" },
    {
      t: "code",
      title: "Notify finance only for large expenses",
      lang: "json",
      code: `{
  "id": "wf_big_expense",
  "name": "Big expense check",
  "trigger": { "type": "webhook", "secret": "choose-a-long-random-secret" },
  "entry": "is_large",
  "limits": { "max_steps": 10, "timeout_seconds": 300, "max_ai_tokens": 0 },
  "nodes": [
    {
      "id": "is_large",
      "type": "condition",
      "params": { "left": "{{trigger.body.amount_usd}}", "op": "greater_than", "right": "100" },
      "on_true": "ask_finance",
      "on_false": "auto_ok"
    },
    {
      "id": "ask_finance",
      "type": "approval",
      "params": { "message": "Expense of {{trigger.body.amount_usd}} USD from {{trigger.body.employee_email}}" },
      "next": "approved_notice"
    },
    {
      "id": "approved_notice",
      "type": "notify",
      "params": { "channel": "email", "to": "{{trigger.body.employee_email}}", "subject": "Approved", "message": "Your expense was approved." },
      "next": null
    },
    {
      "id": "auto_ok",
      "type": "notify",
      "params": { "channel": "email", "to": "{{trigger.body.employee_email}}", "subject": "Auto-approved", "message": "Your expense was auto-approved." },
      "next": null
    }
  ]
}`,
    },
    { t: "h2", text: "When publishing is refused" },
    {
      t: "p",
      text: "Publishing checks the workflow against the node catalog. Problems stop the publish and are listed with a code:",
    },
    {
      t: "table",
      head: ["Code", "Meaning", "Fix"],
      rows: [
        [
          "`invalid_node_type`",
          "A node uses a type that is not one of the seven.",
          "Pick a catalog type.",
        ],
        [
          "`missing_required_param`",
          "A required setting is absent, for example `to` on a `notify`.",
          "Add the setting.",
        ],
        [
          "`invalid_edge_target`",
          "`next` points at a node that does not exist.",
          "Fix the id or use `null`.",
        ],
        ["`invalid_entry`", "The start node does not exist.", "Set `entry` to a real node id."],
        ["`duplicate_node_ids`", "Two nodes share an id.", "Rename one."],
        ["`empty_nodes`", "There are no nodes.", "Add at least one."],
        [
          "`invalid_param_value`",
          "A value is outside its allowed set, such as a bad `channel`.",
          "Use an allowed value.",
        ],
        ["`invalid_template`", "A `{{…}}` placeholder is malformed.", "Check the braces and path."],
      ],
    },
    {
      t: "p",
      text: "Warnings do not block publishing. A common one: “order_action is reachable without an approval node” — a reminder that the engine will refuse to run that node unless an approval was granted earlier in the same run.",
    },
    { t: "h2", text: "Option 2 — the natural-language compiler" },
    {
      t: "steps",
      items: [
        {
          title: "Open the compiler",
          text: "Go to **NL compiler** (`/compiler`).",
        },
        {
          title: "Describe the process",
          text: "Type a sentence like: “When a webhook receives a new order, check if the total amount is over 100 dollars. If it is, ask a manager for approval and then email the customer a confirmation. Otherwise email the confirmation right away.” You can also click an example.",
        },
        {
          title: "Compile and review",
          text: "You get a diagram, the JSON, an explanation and notes. The result is validated and saved as a **draft**.",
        },
        {
          title: "Publish",
          text: "If you are happy, publish the generated draft from the compiler or from its workflow page.",
        },
      ],
    },
    {
      t: "ul",
      items: [
        "The compiler only ever uses the seven catalog node types.",
        "If you ask for something Relay cannot do (“update Salesforce”), it **refuses** and lists what is missing instead of guessing.",
        "If your description includes a refund or replacement without a human check, it **inserts an approval step** and tells you.",
      ],
    },
  ],
};
