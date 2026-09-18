import { cn } from '@/lib/utils';

export function Spinner({ className, size }: { className?: string; size?: 'sm' | 'md' | 'lg' }) {
  const sizeCls = size === 'sm' ? 'w-3.5 h-3.5' : size === 'lg' ? 'w-6 h-6' : 'w-4 h-4';
  return (
    <div
      className={cn(
        'inline-block border-2 border-slate-300 border-t-primary rounded-full animate-spin',
        sizeCls,
        className,
      )}
      aria-label="Loading"
    />
  );
}
