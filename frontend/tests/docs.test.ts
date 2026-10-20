import { describe, expect, it } from "vitest";
import { DEVELOPER_DOCS } from "@/lib/docs/developer";
import { USER_GUIDE } from "@/lib/docs/guide";
import { parseInline, slugify } from "@/lib/docs/inline";
import type { DocBlock, DocCollection } from "@/lib/docs/types";

const COLLECTIONS: DocCollection[] = [USER_GUIDE, DEVELOPER_DOCS];

function strings(b: DocBlock): string[] {
  switch (b.t) {
    case "h2":
    case "h3":
    case "p":
      return [b.text];
    case "ul":
    case "ol":
      return b.items;
    case "table":
      return b.rows.flat();
    case "note":
      return [b.text];
    case "steps":
      return b.items.map((i) => i.text);
    case "code":
      return [];
  }
}

describe("documentation content", () => {
  it.each(COLLECTIONS)("$title has unique slugs and non-empty pages", (c) => {
    const slugs = c.pages.map((p) => p.slug);
    expect(new Set(slugs).size).toBe(slugs.length);
    for (const p of c.pages) {
      expect(p.blocks.length).toBeGreaterThan(0);
      expect(p.summary.length).toBeGreaterThan(10);
    }
  });

  it("every internal doc link points at an existing topic", () => {
    const valid = new Set<string>();
    for (const c of COLLECTIONS) {
      valid.add(c.base);
      c.pages.forEach((p) => valid.add(`${c.base}/${p.slug}`));
    }
    const broken: string[] = [];
    for (const c of COLLECTIONS) {
      for (const p of c.pages) {
        for (const text of p.blocks.flatMap(strings)) {
          for (const t of parseInline(text)) {
            if (t.k !== "link" || !t.href.startsWith("/")) continue;
            if (!valid.has(t.href.split("#")[0] ?? "")) broken.push(`${p.slug} -> ${t.href}`);
          }
        }
      }
    }
    expect(broken).toEqual([]);
  });
});

describe("inline markup", () => {
  it("tokenizes code, bold and links", () => {
    expect(parseInline("a `b` **c** [d](/e) f")).toEqual([
      { k: "text", v: "a " },
      { k: "code", v: "b" },
      { k: "text", v: " " },
      { k: "bold", v: "c" },
      { k: "text", v: " " },
      { k: "link", v: "d", href: "/e" },
      { k: "text", v: " f" },
    ]);
  });

  it("slugifies headings", () => {
    expect(slugify("Docker & hosting!")).toBe("docker-hosting");
  });
});
