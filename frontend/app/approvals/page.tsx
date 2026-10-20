import { ErrorState, PageHeader } from "@/components/States";
import { serverApi } from "@/lib/api/server";
import { ApprovalsBoard } from "./board";

export const dynamic = "force-dynamic";
export const metadata = { title: "Approvals" };

export default async function ApprovalsPage() {
  let all;
  try {
    all = await serverApi().listApprovals();
  } catch (e) {
    return (
      <>
        <PageHeader title="Approvals" />
        <ErrorState error={e} />
      </>
    );
  }
  return (
    <>
      <PageHeader
        title="Approvals"
        description="Runs paused at an approval node. Sensitive actions (order_action) cannot execute until a human approves — the engine checks these records, not the prompt."
      />
      <ApprovalsBoard initial={all} />
    </>
  );
}
