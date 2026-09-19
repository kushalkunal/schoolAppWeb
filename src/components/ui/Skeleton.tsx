import { HTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

/**
 * Shimmer-animated placeholder. Pair with `h-N w-N` to size; the .skeleton class
 * (in globals.css) does the gradient + animation.
 */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('skeleton', className)} {...props} />;
}

/** Convenience for card-shaped loaders. */
export function SkeletonCard() {
  return (
    <div className="rounded-brand border border-slate-200/80 bg-white p-5 space-y-3">
      <Skeleton className="h-4 w-1/3" />
      <Skeleton className="h-8 w-2/3" />
      <Skeleton className="h-3 w-1/2" />
    </div>
  );
}

/** Convenience for table-shaped loaders. */
export function SkeletonRows({ rows = 5 }: { rows?: number }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  );
}
