import type { Metadata } from "next";
import { DocsShell } from "@/components/docs/DocsShell";
import { DEVELOPER_DOCS } from "@/lib/docs/developer";

const [first] = DEVELOPER_DOCS.pages;

export const metadata: Metadata = { title: "Developer guide" };

export default function DocsIndexPage() {
  if (!first) return null;
  return <DocsShell collection={DEVELOPER_DOCS} page={first} />;
}
