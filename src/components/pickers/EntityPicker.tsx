'use client';

/**
 * Generic search-as-you-type combobox. Used by the {@link StudentPicker} and
 * {@link StaffPicker} wrappers — pages should reach for those rather than this raw
 * primitive.
 *
 * Behavior:
 * - Click to open. Type to filter (substring, case-insensitive).
 * - Arrow keys + Enter navigate. Escape closes.
 * - When `value` is set externally and matches one of the options, the input shows the
 *   pretty label instead of the raw ID.
 * - "Clear" button when something's selected.
 */

import { useEffect, useMemo, useRef, useState } from 'react';
import { Check, ChevronsUpDown, X } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface PickerOption {
  id: string;
  label: string;
  /** Optional secondary line (e.g. class name, role) shown small below the label. */
  sub?: string | null;
}

interface EntityPickerProps {
  label: string;
  /** Currently-selected id, or null. */
  value: string | null;
  onChange: (id: string | null) => void;
  options: PickerOption[];
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
  /** Renders a "No matches" line when the search filters out everything. */
  emptyText?: string;
  loading?: boolean;
}

export function EntityPicker({
  label, value, onChange, options, placeholder = 'Select…',
  required, disabled, emptyText = 'No matches', loading,
}: EntityPickerProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [hi, setHi] = useState(0);
  const ref = useRef<HTMLDivElement>(null);

  const selected = options.find((o) => o.id === value) ?? null;
  const filtered = useMemo(() => {
    if (!query.trim()) return options.slice(0, 100);
    const q = query.trim().toLowerCase();
    return options
      .filter((o) => o.label.toLowerCase().includes(q) || (o.sub ?? '').toLowerCase().includes(q))
      .slice(0, 100);
  }, [options, query]);

  useEffect(() => { setHi(0); }, [query, open]);

  // Click outside → close.
  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  function pick(id: string) {
    onChange(id);
    setQuery('');
    setOpen(false);
  }

  return (
    <div ref={ref} className="block relative">
      <label className="text-sm text-slate-700">{label}{required && <span className="text-rose-600"> *</span>}</label>
      <div
        role="combobox"
        aria-expanded={open}
        className={cn(
          'mt-1 flex items-center gap-2 w-full rounded border border-slate-300 px-3 py-2 text-sm cursor-text',
          disabled && 'bg-slate-50 cursor-not-allowed',
          open && 'border-primary ring-2 ring-primary/15',
        )}
        onClick={() => !disabled && setOpen(true)}
      >
        {selected && !open ? (
          <span className="flex-1 truncate">
            <span className="font-medium">{selected.label}</span>
            {selected.sub && <span className="text-slate-500 ml-1.5">· {selected.sub}</span>}
          </span>
        ) : (
          <input
            type="text"
            value={open ? query : ''}
            placeholder={selected ? selected.label : placeholder}
            disabled={disabled}
            onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
            onFocus={() => setOpen(true)}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') { e.preventDefault(); setHi((h) => Math.min(h + 1, filtered.length - 1)); }
              else if (e.key === 'ArrowUp') { e.preventDefault(); setHi((h) => Math.max(h - 1, 0)); }
              else if (e.key === 'Enter')   { e.preventDefault(); if (filtered[hi]) pick(filtered[hi].id); }
              else if (e.key === 'Escape')  { setOpen(false); }
            }}
            className="flex-1 outline-none bg-transparent"
          />
        )}
        {selected && (
          <button type="button" onClick={(e) => { e.stopPropagation(); onChange(null); setQuery(''); }}
            aria-label="Clear" className="text-slate-400 hover:text-slate-700">
            <X size={14} />
          </button>
        )}
        <ChevronsUpDown size={14} className="text-slate-400 shrink-0" />
      </div>

      {open && (
        <div className="absolute z-20 mt-1 w-full max-h-64 overflow-auto bg-white border border-slate-200 rounded shadow-lg">
          {loading && <div className="px-3 py-2 text-sm text-slate-500">Loading…</div>}
          {!loading && filtered.length === 0 && (
            <div className="px-3 py-2 text-sm text-slate-500">{emptyText}</div>
          )}
          {filtered.map((o, i) => (
            <button
              key={o.id}
              type="button"
              onClick={() => pick(o.id)}
              onMouseEnter={() => setHi(i)}
              className={cn(
                'w-full text-left px-3 py-2 text-sm flex items-center gap-2',
                i === hi && 'bg-primary-soft',
              )}
            >
              <Check size={12} className={cn('shrink-0', o.id === value ? 'text-primary' : 'invisible')} />
              <div className="min-w-0 flex-1">
                <div className="truncate">{o.label}</div>
                {o.sub && <div className="text-xs text-slate-500 truncate">{o.sub}</div>}
              </div>
            </button>
          ))}
          {!loading && filtered.length === 100 && (
            <div className="px-3 py-1.5 text-[11px] text-slate-400 border-t border-slate-100">
              Showing first 100 — refine search to see more
            </div>
          )}
        </div>
      )}
    </div>
  );
}
