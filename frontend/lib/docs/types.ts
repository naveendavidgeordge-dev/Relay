/**
 * Documentation content model. Everything under /docs and /guide is static data rendered by
 * components/docs/DocRenderer.tsx, so the pages need no backend and no network.
 *
 * Inline markup supported in every `text` / cell / item string (see lib/docs/inline.ts):
 * backtick code, **bold**, and [label](/internal/path or https://external) links.
 */
export type DocBlock =
  | { t: "h2"; text: string }
  | { t: "h3"; text: string }
  | { t: "p"; text: string }
  | { t: "ul"; items: string[] }
  | { t: "ol"; items: string[] }
  | { t: "code"; code: string; lang?: string; title?: string }
  | { t: "table"; head: string[]; rows: string[][] }
  | { t: "note"; tone: "info" | "tip" | "warn"; title?: string; text: string }
  | { t: "steps"; items: { title: string; text: string }[] };

export interface DocPage {
  slug: string;
  title: string;
  /** One-line summary shown in the section index. */
  summary: string;
  /** Sidebar group heading. */
  group: string;
  blocks: DocBlock[];
}

export interface DocCollection {
  /** URL prefix, e.g. "/docs". */
  base: string;
  title: string;
  tagline: string;
  pages: DocPage[];
}
