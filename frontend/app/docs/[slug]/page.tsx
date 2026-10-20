import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DocsShell } from "@/components/docs/DocsShell";
import { DEVELOPER_DOCS } from "@/lib/docs/developer";

type Params = { params: { slug: string } };

export const dynamicParams = false;

export function generateStaticParams() {
  return DEVELOPER_DOCS.pages.map((p) => ({ slug: p.slug }));
}

export function generateMetadata({ params }: Params): Metadata {
  const page = DEVELOPER_DOCS.pages.find((p) => p.slug === params.slug);
  return { title: page ? `${page.title} · Developer guide` : "Developer guide" };
}

export default function DocsTopicPage({ params }: Params) {
  const page = DEVELOPER_DOCS.pages.find((p) => p.slug === params.slug);
  if (!page) notFound();
  return <DocsShell collection={DEVELOPER_DOCS} page={page} />;
}
