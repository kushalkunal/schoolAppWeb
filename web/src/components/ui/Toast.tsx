'use client';

import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2, Info, X, AlertTriangle } from 'lucide-react';
import { cn } from '@/lib/utils';

export type ToastTone = 'success' | 'error' | 'info' | 'warning';

interface Toast {
  id: number;
  tone: ToastTone;
  title?: string;
  message: string;
  /** Milliseconds before auto-dismiss; 0 = sticky. */
  ttl: number;
}

interface ToastApi {
  toast: (input: { tone?: ToastTone; title?: string; message: string; ttl?: number }) => void;
  success: (message: string, title?: string) => void;
  error:   (message: string, title?: string) => void;
  info:    (message: string, title?: string) => void;
  warning: (message: string, title?: string) => void;
}

const Ctx = createContext<ToastApi | null>(null);

/**
 * App-wide toast bus. Mount once at the top of the layout; call {@link useToast} anywhere.
 * Toasts stack in the bottom-right; auto-dismiss after 5s unless `ttl: 0` is passed.
 */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<Toast[]>([]);

  const remove = useCallback((id: number) => {
    setItems((arr) => arr.filter((t) => t.id !== id));
  }, []);

  const push = useCallback((input: { tone?: ToastTone; title?: string; message: string; ttl?: number }) => {
    const id = Date.now() + Math.random();
    const t: Toast = {
      id, tone: input.tone ?? 'info',
      title: input.title, message: input.message,
      ttl: input.ttl ?? 5000,
    };
    setItems((arr) => [...arr, t]);
    if (t.ttl > 0) setTimeout(() => remove(id), t.ttl);
  }, [remove]);

  const api: ToastApi = {
    toast:   push,
    success: (message, title) => push({ tone: 'success', title, message }),
    error:   (message, title) => push({ tone: 'error',   title, message }),
    info:    (message, title) => push({ tone: 'info',    title, message }),
    warning: (message, title) => push({ tone: 'warning', title, message }),
  };

  return (
    <Ctx.Provider value={api}>
      {children}
      {/* Stack container — top of body, fixed, contains all visible toasts. */}
      <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm w-[calc(100vw-2rem)]">
        {items.map((t) => <ToastView key={t.id} toast={t} onClose={() => remove(t.id)} />)}
      </div>
    </Ctx.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(Ctx);
  if (!ctx) {
    // Soft fallback so tests + storybook-style isolated renders don't crash.
    const noop = () => {};
    return { toast: noop, success: noop, error: noop, info: noop, warning: noop };
  }
  return ctx;
}

function ToastView({ toast, onClose }: { toast: Toast; onClose: () => void }) {
  const { tone, title, message } = toast;
  const conf = {
    success: { Icon: CheckCircle2, bar: 'bg-success', chip: 'bg-success/10 text-success' },
    error:   { Icon: AlertCircle,  bar: 'bg-danger',  chip: 'bg-danger/10  text-danger' },
    warning: { Icon: AlertTriangle, bar: 'bg-warning', chip: 'bg-warning/10 text-warning' },
    info:    { Icon: Info,         bar: 'bg-info',    chip: 'bg-info/10    text-info' },
  }[tone];

  // Mount-fade in on first render.
  const [visible, setVisible] = useState(false);
  useEffect(() => { setVisible(true); }, []);

  return (
    <div
      role="status"
      className={cn(
        'group relative bg-white rounded-brand border border-slate-200 shadow-lift',
        'pl-4 pr-3 py-3 flex items-start gap-3 overflow-hidden',
        'transition-all duration-200',
        visible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      )}
    >
      <span className={cn('absolute left-0 top-0 bottom-0 w-1', conf.bar)} />
      <span className={cn('w-7 h-7 grid place-items-center rounded-full shrink-0', conf.chip)}>
        <conf.Icon size={16} />
      </span>
      <div className="flex-1 min-w-0">
        {title && <div className="text-sm font-semibold text-slate-900">{title}</div>}
        <div className="text-sm text-slate-700">{message}</div>
      </div>
      <button
        onClick={onClose}
        className="text-slate-400 hover:text-slate-700 rounded p-1"
        aria-label="Dismiss"
      >
        <X size={14} />
      </button>
    </div>
  );
}
