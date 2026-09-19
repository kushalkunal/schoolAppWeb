import { ReactNode } from 'react';
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Card } from './Card';

interface StatProps {
  label: string;
  value: ReactNode;
  icon?: ReactNode;
  /** Optional sub-line (e.g. "vs last week"). */
  hint?: ReactNode;
  /** Numerical change percentage; sign drives the arrow + colour. */
  changePct?: number | null;
  /** Tone of the icon chip. Defaults to brand primary. */
  tone?: 'primary' | 'accent' | 'success' | 'warning' | 'danger' | 'info';
  className?: string;
}

/**
 * Dashboard KPI tile. Designed to sit in a grid of 3-4 across.
 * Numbers render in a slightly tabular font for visual alignment.
 */
export function Stat({ label, value, icon, hint, changePct, tone = 'primary', className }: StatProps) {
  const chips: Record<NonNullable<StatProps['tone']>, string> = {
    primary: 'bg-primary-soft  text-primary',
    accent:  'bg-accent-soft   text-accent',
    success: 'bg-success/10    text-success',
    warning: 'bg-warning/10    text-warning',
    danger:  'bg-danger/10     text-danger',
    info:    'bg-info/10       text-info',
  };
  return (
    <Card className={cn('flex items-start gap-4', className)} padding="md">
      {icon && (
        <div className={cn('w-10 h-10 grid place-items-center rounded-brand shrink-0', chips[tone])}>
          {icon}
        </div>
      )}
      <div className="min-w-0 flex-1">
        <div className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</div>
        <div className="mt-1 text-2xl font-semibold text-slate-900 tabular-nums truncate">{value}</div>
        <div className="mt-1 flex items-center gap-2 text-xs text-slate-500">
          {changePct != null && <ChangeChip pct={changePct} />}
          {hint && <span>{hint}</span>}
        </div>
      </div>
    </Card>
  );
}

function ChangeChip({ pct }: { pct: number }) {
  const Icon = pct > 0 ? ArrowUpRight : pct < 0 ? ArrowDownRight : Minus;
  const color = pct > 0 ? 'text-success' : pct < 0 ? 'text-danger' : 'text-slate-500';
  return (
    <span className={cn('inline-flex items-center gap-0.5 font-medium', color)}>
      <Icon size={12} />
      {Math.abs(pct).toFixed(1)}%
    </span>
  );
}
