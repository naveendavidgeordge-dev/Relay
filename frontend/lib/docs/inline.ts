export type InlineToken =
  | { k: "text"; v: string }
  | { k: "code"; v: string }
  | { k: "bold"; v: string }
  | { k: "link"; v: string; href: string };

const PATTERN = /`([^`]+)`|\*\*([^*]+)\*\*|\[([^\]]+)\]\(([^)\s]+)\)/g;

/** Tiny inline-markup tokenizer (code, bold, links). Anything else is literal text. */
export function parseInline(src: string): InlineToken[] {
  const out: InlineToken[] = [];
  let last = 0;
  for (const m of src.matchAll(PATTERN)) {
    const at = m.index ?? 0;
    if (at > last) out.push({ k: "text", v: src.slice(last, at) });
    if (m[1] !== undefined) out.push({ k: "code", v: m[1] });
    else if (m[2] !== undefined) out.push({ k: "bold", v: m[2] });
    else out.push({ k: "link", v: m[3] ?? "", href: m[4] ?? "#" });
    last = at + m[0].length;
  }
  if (last < src.length) out.push({ k: "text", v: src.slice(last) });
  return out;
}

/** Stable anchor id for a heading. */
export function slugify(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}
