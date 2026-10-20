import { Skeleton } from "@/components/ui/primitives";

/** Route-level skeleton shown while server components fetch. */
export default function Loading() {
  return (
    <div aria-busy="true" aria-label="Loading">
      <Skeleton className="mb-6 h-8 w-48" />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="h-32" />
        ))}
      </div>
      <Skeleton className="mt-6 h-72" />
    </div>
  );
}
