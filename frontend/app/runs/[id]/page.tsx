import { notFound } from "next/navigation";
import { ErrorState } from "@/components/States";
import { RelayApiError } from "@/lib/api/client";
import { serverApi } from "@/lib/api/server";
import { RunDetail } from "./run-detail";

export const dynamic = "force-dynamic";

export async function generateMetadata({ params }: { params: { id: string } }) {
  return { title: `Run ${params.id}`, description: "Step-by-step trace of a Relay run." };
}

export default async function RunPage({ params }: { params: { id: string } }) {
  try {
    const run = await serverApi().getRun(params.id);
    return <RunDetail initial={run} />;
  } catch (e) {
    if (e instanceof RelayApiError && e.status === 404) notFound();
    return <ErrorState error={e} />;
  }
}
