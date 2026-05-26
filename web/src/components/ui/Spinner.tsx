import { cn } from '@/lib/utils';

export function Spinner({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        'inline-block w-4 h-4 border-2 border-slate-300 border-t-primary rounded-full animate-spin',
        className,
      )}
      aria-label="Loading"
    />
  );
}
