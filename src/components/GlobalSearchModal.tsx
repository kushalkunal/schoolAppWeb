'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Search, X, User, ChevronRight } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { studentsApi } from '@/api/endpoints/students';
import { Spinner } from '@/components/ui/Spinner';
import { cn } from '@/lib/utils';

interface Props {
  tenantId: string;
  open: boolean;
  onClose: () => void;
}

export function GlobalSearchModal({ tenantId, open, onClose }: Props) {
  const router = useRouter();
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  // Auto-focus input when modal opens
  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 50);
      setQuery('');
      setDebounced('');
    }
  }, [open]);

  // Debounce 300ms
  useEffect(() => {
    const t = setTimeout(() => setDebounced(query), 300);
    return () => clearTimeout(t);
  }, [query]);

  // Keyboard: Escape to close
  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, onClose]);

  const searchQ = useQuery({
    queryKey: ['global-student-search', tenantId, debounced],
    queryFn: () => studentsApi.list(tenantId, { search: debounced, size: 8 }),
    enabled: !!tenantId && debounced.length >= 2,
    staleTime: 15_000,
  });

  function handleSelect(studentId: string) {
    onClose();
    router.push(`/tenants/${tenantId}/students/${studentId}/dashboard`);
  }

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center pt-16 sm:pt-24 px-4 bg-black/50 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="w-full max-w-xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Search input */}
        <div className="flex items-center gap-3 px-4 py-3 border-b border-slate-200">
          <Search size={18} className="text-slate-400 shrink-0" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search student by name, admission no., class…"
            className="flex-1 text-sm text-slate-900 placeholder:text-slate-400 bg-transparent outline-none"
          />
          {query ? (
            <button
              onClick={() => { setQuery(''); setDebounced(''); }}
              className="text-slate-400 hover:text-slate-600 transition"
            >
              <X size={16} />
            </button>
          ) : (
            <kbd className="hidden sm:inline-flex items-center gap-1 px-1.5 py-0.5 text-[10px] text-slate-400 bg-slate-100 rounded border border-slate-200">
              ESC
            </kbd>
          )}
        </div>

        {/* Results */}
        <div className="max-h-80 overflow-y-auto">
          {debounced.length < 2 && (
            <div className="px-4 py-8 text-center text-sm text-slate-400">
              Type at least 2 characters to search students
            </div>
          )}
          {debounced.length >= 2 && searchQ.isLoading && (
            <div className="flex justify-center py-8">
              <Spinner />
            </div>
          )}
          {debounced.length >= 2 && !searchQ.isLoading && searchQ.data?.items.length === 0 && (
            <div className="px-4 py-8 text-center text-sm text-slate-400">
              No students found for &ldquo;{debounced}&rdquo;
            </div>
          )}
          {searchQ.data && searchQ.data.items.length > 0 && (
            <ul>
              {searchQ.data.items.map((student, idx) => (
                <li key={student.id}>
                  <button
                    onClick={() => handleSelect(student.id)}
                    className={cn(
                      'w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-slate-50 transition',
                      idx < searchQ.data.items.length - 1 && 'border-b border-slate-100',
                    )}
                  >
                    {/* Avatar */}
                    <div className="w-9 h-9 rounded-full bg-primary/10 text-primary grid place-items-center shrink-0">
                      <User size={16} />
                    </div>
                    {/* Info */}
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-slate-900 truncate">{student.displayName}</div>
                      <div className="flex items-center gap-2 text-xs text-slate-500 mt-0.5">
                        {student.admissionNumber && (
                          <span>Adm: {student.admissionNumber}</span>
                        )}
                        {student.gender && (
                          <>
                            <span className="text-slate-300">·</span>
                            <span className="capitalize">{student.gender.toLowerCase()}</span>
                          </>
                        )}
                      </div>
                    </div>
                    <ChevronRight size={14} className="text-slate-300 shrink-0" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Footer hint */}
        <div className="px-4 py-2.5 border-t border-slate-100 bg-slate-50/50 flex items-center justify-between">
          <span className="text-xs text-slate-400">Click a student to view their performance dashboard</span>
          <span className="text-xs text-slate-400 hidden sm:inline">↵ to open · ESC to close</span>
        </div>
      </div>
    </div>
  );
}
