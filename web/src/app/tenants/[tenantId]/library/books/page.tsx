'use client';

import { useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BookOpen, Plus, Search, Trash2 } from 'lucide-react';
import { libraryApi, type BookDto, type CreateBookRequest } from '@/api/endpoints/library';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { hasCode, isApiError } from '@/api/errors';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';

export default function BooksPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const [search, setSearch] = useState('');
  const [createOpen, setCreateOpen] = useState(false);

  const q = useQuery({
    queryKey: ['library-books', tenantId],
    queryFn: () => libraryApi.listBooks(tenantId),
    enabled: !!tenantId,
    retry: false,
  });

  const filtered = useMemo(() => {
    const list = q.data ?? [];
    if (!search.trim()) return list;
    const s = search.toLowerCase();
    return list.filter((b) =>
      b.title.toLowerCase().includes(s) ||
      (b.author ?? '').toLowerCase().includes(s) ||
      (b.isbn ?? '').toLowerCase().includes(s),
    );
  }, [q.data, search]);

  const deactivate = useMutation({
    mutationFn: (bookId: string) => libraryApi.deactivateBook(tenantId, bookId),
    onSuccess: () => {
      toast.success('Book removed');
      qc.invalidateQueries({ queryKey: ['library-books', tenantId] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not remove'),
  });

  if (q.isError && hasCode(q.error, 'FEATURE_DISABLED')) {
    return (
      <EmptyState
        icon={<BookOpen size={28} />}
        title="Library isn't enabled for your plan"
        description="Track your book catalogue, issue and return, with fines for late returns."
      />
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex items-end gap-3">
        <label className="relative flex-1 max-w-sm">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by title, author, or ISBN…"
            className="pl-9 pr-3 py-2 rounded-brand border border-slate-300 text-sm w-full focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          />
        </label>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button className="ml-auto" onClick={() => setCreateOpen(true)}>
            <Plus size={14} /> Add book
          </Button>
        </RequireRole>
      </div>

      {q.isLoading && <Skeleton className="h-64" />}
      {q.isError && !hasCode(q.error, 'FEATURE_DISABLED') && (
        <ErrorBanner error={q.error} onRetry={() => q.refetch()} />
      )}

      {q.data && filtered.length === 0 && (
        <EmptyState
          icon={<BookOpen size={28} />}
          title={search ? 'No books match your search' : 'No books yet'}
          description={search ? 'Try a different keyword.' : 'Add your first book to start the catalogue.'}
          action={!search && (
            <RequireRole roles={OWNER_OR_ADMIN}>
              <Button onClick={() => setCreateOpen(true)}><Plus size={14} /> Add book</Button>
            </RequireRole>
          )}
        />
      )}

      {filtered.length > 0 && (
        <Card padding="none" className="overflow-hidden">
          <CardHeader>
            <CardTitle>{filtered.length} {filtered.length === 1 ? 'book' : 'books'}</CardTitle>
            <CardDescription>Total available across the library</CardDescription>
          </CardHeader>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-[11px] uppercase tracking-wide text-slate-500 border-b border-slate-200">
                  <th className="text-left py-2.5 px-5">Title</th>
                  <th className="text-left">Author</th>
                  <th className="text-left">ISBN</th>
                  <th className="text-left">Category</th>
                  <th className="text-right">Available / Total</th>
                  <th className="px-5"></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((b) => <BookRow key={b.id} book={b}
                  onDelete={() => {
                    if (confirm(`Remove "${b.title}"?`)) deactivate.mutate(b.id);
                  }} />)}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <CreateBookModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        tenantId={tenantId}
      />
    </div>
  );
}

function BookRow({ book, onDelete }: { book: BookDto; onDelete: () => void }) {
  const stockLow = book.availableCopies === 0;
  const stockMedium = book.availableCopies > 0 && book.availableCopies < book.totalCopies / 4;
  return (
    <tr className="border-b border-slate-100 hover:bg-slate-50/60 transition">
      <td className="py-3 px-5">
        <div className="font-medium text-slate-900">{book.title}</div>
      </td>
      <td className="text-slate-600">{book.author ?? '—'}</td>
      <td className="text-slate-500 font-mono text-xs">{book.isbn ?? '—'}</td>
      <td>
        {book.category ? <Badge tone="neutral" size="sm">{book.category}</Badge> : <span className="text-slate-400">—</span>}
      </td>
      <td className="text-right">
        <Badge tone={stockLow ? 'danger' : stockMedium ? 'warning' : 'success'} size="sm">
          {book.availableCopies} / {book.totalCopies}
        </Badge>
      </td>
      <td className="px-5 text-right">
        <RequireRole roles={OWNER_OR_ADMIN}>
          <button onClick={onDelete} className="text-slate-400 hover:text-danger p-1 rounded transition">
            <Trash2 size={14} />
          </button>
        </RequireRole>
      </td>
    </tr>
  );
}

function CreateBookModal({ open, onClose, tenantId }: {
  open: boolean; onClose: () => void; tenantId: string;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const initial: CreateBookRequest = {
    title: '', totalCopies: 1, availableCopies: 1,
  };
  const [form, setForm] = useState<CreateBookRequest>(initial);

  const create = useMutation({
    mutationFn: () => libraryApi.createBook(tenantId, form),
    onSuccess: () => {
      toast.success('Book added to catalogue');
      qc.invalidateQueries({ queryKey: ['library-books', tenantId] });
      onClose();
      setForm(initial);
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not add book'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Add book">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        <Input label="Title" required value={form.title}
          onChange={(e) => setForm({ ...form, title: e.target.value })} />
        <Input label="Author" value={form.author ?? ''}
          onChange={(e) => setForm({ ...form, author: e.target.value })} />
        <div className="grid grid-cols-2 gap-3">
          <Input label="ISBN" value={form.isbn ?? ''}
            onChange={(e) => setForm({ ...form, isbn: e.target.value })} />
          <Input label="Publisher" value={form.publisher ?? ''}
            onChange={(e) => setForm({ ...form, publisher: e.target.value })} />
        </div>
        <Input label="Category" placeholder="Fiction, Science, Reference…" value={form.category ?? ''}
          onChange={(e) => setForm({ ...form, category: e.target.value })} />
        <div className="grid grid-cols-2 gap-3">
          <Input label="Total copies" type="number" min={1} required value={form.totalCopies}
            onChange={(e) => {
              const n = Number(e.target.value);
              setForm({ ...form, totalCopies: n, availableCopies: Math.min(form.availableCopies, n) });
            }} />
          <Input label="Available now" type="number" min={0} max={form.totalCopies}
            required value={form.availableCopies}
            onChange={(e) => setForm({ ...form, availableCopies: Number(e.target.value) })} />
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={create.isPending} disabled={!form.title}>Add book</Button>
        </div>
      </form>
    </Modal>
  );
}
