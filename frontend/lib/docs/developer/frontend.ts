import type { DocPage } from "../types";

export const frontend: DocPage = {
  slug: "frontend",
  group: "Services",
  title: "Frontend (this console)",
  summary:
    "App Router structure, data fetching, the API proxy, design tokens and how to add a page.",
  blocks: [
    {
      t: "p",
      text: "`frontend/` is a Next.js 14 App Router application in TypeScript (`strict`, no `any`) styled with Tailwind and Radix primitives. It is an npm workspace, so install and run it from the repository root.",
    },
    {
      t: "code",
      lang: "bash",
      code: `npm install
npm --workspace frontend run dev          # http://localhost:3000
npm --workspace frontend run build        # production build (output: standalone)
npm --workspace frontend run start        # serve the build
npm --workspace frontend run test         # Vitest + React Testing Library
npm --workspace frontend run lint         # next lint --max-warnings 0
npm --workspace frontend run typecheck    # tsc --noEmit
npm --workspace frontend run format       # prettier --write .`,
    },
    { t: "h2", text: "Directory map" },
    {
      t: "code",
      lang: "text",
      code: `frontend/
├─ app/                     routes (App Router)
│  ├─ layout.tsx            fonts, <Providers>, <AppShell>
│  ├─ page.tsx              Dashboard
│  ├─ workflows/            list, [id] detail, new (JSON editor)
│  ├─ runs/                 list + filters, [id] run detail
│  ├─ approvals/            pending cards + history
│  ├─ compiler/             natural-language compiler
│  ├─ docs/, guide/         this documentation (static)
│  └─ api/relay/[...path]/  same-origin proxy to the Relay API
├─ components/              AppShell, CommandPalette, StepTimeline, WorkflowCanvas, ui/*, docs/*
├─ lib/
│  ├─ api/                  client.ts (typed + Zod), server.ts, browser.ts, types.ts
│  ├─ docs/                 documentation content and parsing helpers
│  ├─ metrics.ts, utils.ts
├─ tests/                   Vitest suites and fixtures
├─ tailwind.config.ts       maps semantic colour names onto CSS variables
└─ next.config.mjs          output: "standalone", workspace tracing root`,
    },
    { t: "h2", text: "How data reaches the screen" },
    {
      t: "table",
      head: ["Where", "Client", "Auth"],
      rows: [
        [
          "Server components (pages that list workflows, runs, approvals)",
          "`lib/api/server.ts` → `serverApi()`",
          'Calls `RELAY_API_URL` directly with `RELAY_API_TOKEN` and `cache: "no-store"`.',
        ],
        [
          "Client components (Trigger dialog, Approve button, JSON editor, command palette)",
          "`lib/api/browser.ts` → `browserApi`",
          "Calls `/api/relay/*`; the route handler adds the token.",
        ],
      ],
    },
    {
      t: "p",
      text: "`lib/api/client.ts` builds a typed client. **Every response is parsed with a Zod schema** from `lib/api/types.ts`, so contract drift fails loudly at the edge instead of rendering `undefined` deep in a component. Errors become `RelayApiError` with the backend’s `{error: {message, code, trace_id}}` body.",
    },
    { t: "h3", text: "The proxy route" },
    {
      t: "p",
      text: "`app/api/relay/[...path]/route.ts` forwards `GET`, `POST` and `PUT` only for the prefixes `workflows`, `runs`, `approvals`, `catalog` and `samples`. Everything else — notably `/hooks` — returns 404, because webhooks are for external callers. If the API is down it answers `502 upstream_unavailable`.",
    },
    { t: "h2", text: "Design system" },
    {
      t: "ul",
      items: [
        "Colours are CSS variables in `app/globals.css` (dark is the default, light is a toggle through `next-themes`). `tailwind.config.ts` exposes semantic names — `bg`, `surface`, `elevated`, `border`, `fg`, `muted`, `subtle`, `accent`, `ok`, `warn`, `bad`, `info` — so components never hard-code a palette.",
        "Type scale: `2xs` 12, `xs` 13, `sm` 14, `base` 16, `lg` 20, `xl` 24, `2xl` 32 px. Fonts: Inter and JetBrains Mono.",
        "Shared primitives live in `components/ui` (button, dialog, tabs, tooltip, Card, Badge, Table). Use `cn()` from `lib/utils.ts` to merge class names.",
        "Accessibility: visible focus rings everywhere, a skip-to-content link, `aria-current` on navigation, reduced-motion support.",
      ],
    },
    { t: "h2", text: "Adding a page" },
    {
      t: "steps",
      items: [
        {
          title: "Create the route",
          text: "Add `app/<name>/page.tsx`. Make it a server component and fetch with `serverApi()` unless it needs interactivity.",
        },
        {
          title: "Add it to the sidebar",
          text: "Append an entry to `NAV` in `components/AppShell.tsx` (icon from `lucide-react`).",
        },
        {
          title: "Add it to search",
          text: "Add `{ href, label }` to `PAGES` in `components/CommandPalette.tsx`.",
        },
        {
          title: "Validate new API data",
          text: "Add a Zod schema to `lib/api/types.ts` and a method on the client — never `any`.",
        },
        {
          title: "Test",
          text: "Add a Vitest test under `tests/` and run `npm --workspace frontend run test`, `lint` and `typecheck`.",
        },
      ],
    },
    { t: "h2", text: "How this documentation is built" },
    {
      t: "p",
      text: "Both `/docs` (this Developer guide) and `/guide` (the User guide) are **frontend-only**: no API calls, no database, no markdown files read at runtime.",
    },
    {
      t: "ul",
      items: [
        "Content is typed data in `lib/docs/` — `guide.ts` and `developer.ts` assemble pages from the files in `lib/docs/guide/` and `lib/docs/developer/`. A page is `{ slug, title, summary, group, blocks[] }`.",
        "A block is a heading, paragraph, list, code, table, note or numbered-steps item (`lib/docs/types.ts`). Inline `code`, **bold** and `[links](/path)` are parsed by `lib/docs/inline.ts`.",
        "`components/docs/DocRenderer.tsx` renders blocks; `DocsShell.tsx` adds the section switcher, the topic tab list (`DocsNav.tsx`), an “On this page” outline and previous/next links.",
        "Each topic is its own route — `app/docs/[slug]/page.tsx` and `app/guide/[slug]/page.tsx` use `generateStaticParams`, so every topic is pre-rendered, deep-linkable and has its own page title. `/docs` and `/guide` show the first topic.",
      ],
    },
    {
      t: "steps",
      items: [
        {
          title: "Add a topic",
          text: "Create a `DocPage` object in the matching `lib/docs/*/` file and add it to the `pages` array in `lib/docs/guide.ts` or `lib/docs/developer.ts`. Order in the array is the tab order.",
        },
        {
          title: "Use an existing group or a new one",
          text: "`group` is just the heading in the tab list; pages with the same group are shown together.",
        },
        { title: "Link to it", text: "Use `[text](/docs/<slug>)` in any other page." },
      ],
    },
    { t: "h2", text: "Production build" },
    {
      t: "p",
      text: '`output: "standalone"` makes `next build` emit a self-contained server in `.next/standalone`. Because of npm workspaces, `experimental.outputFileTracingRoot` points at the repository root so hoisted dependencies are included; the entry point is `.next/standalone/frontend/server.js`. The Docker image copies that plus `.next/static` and `public`. See [Docker & hosting](/docs/docker).',
    },
  ],
};
