"use client";

import { ErrorState } from "@/components/States";
import { Button } from "@/components/ui/button";

export default function RouteError({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div className="space-y-4">
      <ErrorState error={error} />
      <Button onClick={reset}>Try again</Button>
    </div>
  );
}
