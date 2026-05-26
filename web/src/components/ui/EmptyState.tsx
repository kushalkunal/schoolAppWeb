import { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}

/**
 * Friendly empty placeholder. Every list page renders one of these when its data is empty
 * — so a school's first day doesn't show scary blank boxes, but instead a clear next step.
 */
export function EmptyState({ icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center text-center py-14 px-6 rounded-brand',
        'bg-white border border-dashed border-slate-300',
        className,
      )}
    >
      {icon && (
        <div className="w-14 h-14 rounded-full bg-primary-soft text-primary grid place-items-center mb-4">
          {icon}
        </div>
      )}
      <h3 className="text-base font-semibold text-slate-900">{title}</h3>
      {description && <p className="text-sm text-slate-500 mt-1.5 max-w-md">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
