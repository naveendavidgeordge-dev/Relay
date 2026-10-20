import type { Metadata } from "next";
import { DocsShell } from "@/components/docs/DocsShell";
import { USER_GUIDE } from "@/lib/docs/guide";

const [first] = USER_GUIDE.pages;

export const metadata: Metadata = { title: "User guide" };

export default function GuideIndexPage() {
  if (!first) return null;
  return <DocsShell collection={USER_GUIDE} page={first} />;
}
