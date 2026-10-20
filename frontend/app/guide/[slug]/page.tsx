import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DocsShell } from "@/components/docs/DocsShell";
import { USER_GUIDE } from "@/lib/docs/guide";

type Params = { params: { slug: string } };

export const dynamicParams = false;

export function generateStaticParams() {
  return USER_GUIDE.pages.map((p) => ({ slug: p.slug }));
}

export function generateMetadata({ params }: Params): Metadata {
  const page = USER_GUIDE.pages.find((p) => p.slug === params.slug);
  return { title: page ? `${page.title} · User guide` : "User guide" };
}

export default function GuideTopicPage({ params }: Params) {
  const page = USER_GUIDE.pages.find((p) => p.slug === params.slug);
  if (!page) notFound();
  return <DocsShell collection={USER_GUIDE} page={page} />;
}
