import { EmptyState } from "@/components/States";

export default function NotFound() {
  return (
    <div className="card">
      <EmptyState
        title="Not found"
        description="That workflow, run or page does not exist."
        action={{ href: "/", label: "Back to dashboard" }}
      />
    </div>
  );
}
