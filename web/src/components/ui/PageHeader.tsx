import { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface PageHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  /** Right-aligned action area — typically `<Button>Primary CTA</Button>`. */
  actions?: ReactNode;
  /** Optional breadcrumb / back link rendered above the title. */
  breadcrumb?: ReactNode;
  /** Optional icon shown next to the title. */
  icon?: ReactNode;
  className?: string;
}

/**
 * Consistent page header — every tenant page uses this so navigation, titles,
 * and CTAs land in the same place visually. Helps muscle memory across the app.
 */
export function PageHeader({ title, description, actions, breadcrumb, icon, className }: PageHeaderProps) {
  return (
    <div className={cn('flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between', className)}>
      <div className="min-w-0">
        {breadcrumb && <div className="mb-1 text-xs text-slate-500">{breadcrumb}</div>}
        <div className="flex items-center gap-3">
          {icon && (
            <div className="w-9 h-9 grid place-items-center rounded-brand bg-primary-soft text-primary shrink-0">
              {icon}
            </div>
          )}
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold text-slate-900 truncate">{title}</h1>
            {description && <p className="text-sm text-slate-500 mt-0.5">{description}</p>}
          </div>
        </div>
      </div>
      {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
    </div>
  );
}
