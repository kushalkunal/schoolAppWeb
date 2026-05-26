'use client';

import { useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  BookCopy, Plus, RotateCcw, AlertTriangle, Calendar, User, BookOpen, Clock,
} from 'lucide-react';
import { libraryApi, type IssueDto, type BookDto } from '@/api/endpoints/library';
import { studentsApi } from '@/api/endpoints/students';
import { StudentPicker } from '@/components/pickers/StudentPicker';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Badge } from '@/components/ui/Badge';
import { Stat } from '@/components/ui/Stat';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { hasCode, isApiError } from '@/api/errors';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { cn } from '@/lib/utils';

export default function IssuesPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const [issueOpen, setIssueOpen] = useState(false);

  const issuesQ = useQuery({
    queryKey: ['library-issues', tenantId],
    queryFn: () => libraryApi.outstanding(tenantId),
    enabled: !!tenantId,
    retry: false,
  });
  const booksQ = useQuery({
    queryKey: ['library-books', tenantId],
    queryFn: () => libraryApi.listBooks(tenantId),
    enabled: !!tenantId,
    retry: false,
  });
  const studentsQ = useQuery({
    queryKey: ['students', tenantId, 'all'],
    queryFn: () => studentsApi.list(tenantId, { size: 500 }),
    enabled: !!tenantId,
  });

  const booksById = useMemo(() => {
    const m = new Map<string, BookDto>();
    for (const b of booksQ.data ?? []) m.set(b.id, b);
    return m;
  }, [booksQ.data]);

  const studentsById = useMemo(() => {
    const m = new Map<string, string>();
    for (const s of studentsQ.data?.items ?? []) m.set(s.id, s.displayName);
    return m;
  }, [studentsQ.data]);

  const today = new Date().toISOString().slice(0, 10);
  const counts = useMemo(() => {
    const list = issuesQ.data ?? [];
    const overdue = list.filter((i) => !i.returnedAt && i.dueDate < today).length;
    const totalOpen = list.length;
    const totalFine = list.reduce((acc, i) => acc + (i.finePaise || 0), 0);
    return { totalOpen, overdue, totalFine };
  }, [issuesQ.data, today]);

  const returnBook = useMutation({
    mutationFn: (issueId: string) => libraryApi.returnBook(tenantId, issueId),
    onSuccess: (issue) => {
      const fineNote = issue.finePaise > 0 ? ` · Fine ₹${(issue.finePaise / 100).toLocaleString('en-IN')}` : '';
      toast.success(`Book returned${fineNote}`);
      qc.invalidateQueries({ queryKey: ['library-issues', tenantId] });
      qc.invalidateQueries({ queryKey: ['library-books', tenantId] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not return book'),
  });

  if (issuesQ.isError && hasCode(issuesQ.error, 'FEATURE_DISABLED')) {
    return (
      <EmptyState
        icon={<BookCopy size={28} />}
        title="Library isn't enabled for your plan"
        description="Issue and return books with automatic fine calculation."
      />
    );
  }

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <Stat label="Books on loan" value={counts.totalOpen} icon={<BookCopy size={16} />} tone="primary" />
        <Stat label="Overdue" value={counts.overdue}
              hint={counts.overdue === 0 ? 'All on time' : 'Past due date'}
              icon={<AlertTriangle size={16} />}
              tone={counts.overdue > 0 ? 'danger' : 'success'} />
        <Stat label="Total fine due" value={`₹${(counts.totalFine / 100).toLocaleString('en-IN')}`}
              icon={<AlertTriangle size={16} />}
              tone={counts.totalFine > 0 ? 'warning' : 'success'} />
      </div>

      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">Books currently issued</h2>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button onClick={() => setIssueOpen(true)}><Plus size={14} /> Issue book</Button>
        </RequireRole>
      </div>

      {issuesQ.isLoading && <Skeleton className="h-64" />}
      {issuesQ.isError && !hasCode(issuesQ.error, 'FEATURE_DISABLED') && (
        <ErrorBanner error={issuesQ.error} onRetry={() => issuesQ.refetch()} />
      )}

      {issuesQ.data && issuesQ.data.length === 0 && (
        <EmptyState
          icon={<BookCopy size={28} />}
          title="No books currently out"
          description="Issue a book to a student to start tracking returns."
          action={
            <RequireRole roles={OWNER_OR_ADMIN}>
              <Button onClick={() => setIssueOpen(true)}><Plus size={14} /> Issue book</Button>
            </RequireRole>
          }
        />
      )}

      {issuesQ.data && issuesQ.data.length > 0 && (
        <Card padding="none" className="overflow-hidden">
          <ul className="divide-y divide-slate-100">
            {issuesQ.data.map((i) => (
              <IssueRow key={i.id}
                issue={i}
                book={booksById.get(i.bookId)}
                studentName={studentsById.get(i.studentId) ?? 'Unknown student'}
                onReturn={() => returnBook.mutate(i.id)}
                busy={returnBook.isPending} />
            ))}
          </ul>
        </Card>
      )}

      <IssueBookModal
        open={issueOpen}
        onClose={() => setIssueOpen(false)}
        tenantId={tenantId}
        books={booksQ.data ?? []}
      />
    </div>
  );
}

function IssueRow({
  issue, book, studentName, onReturn, busy,
}: {
  issue: IssueDto;
  book: BookDto | undefined;
  studentName: string;
  onReturn: () => void;
  busy: boolean;
}) {
  const today = new Date().toISOString().slice(0, 10);
  const overdue = issue.dueDate < today;
  const daysOverdue = overdue
    ? Math.floor((Date.parse(today) - Date.parse(issue.dueDate)) / (1000 * 60 * 60 * 24))
    : 0;
  return (
    <li className="p-4 flex items-center gap-3">
      <span className="w-10 h-10 rounded-brand bg-primary-soft text-primary grid place-items-center shrink-0">
        <BookOpen size={16} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-semibold text-slate-900 truncate">{book?.title ?? 'Unknown book'}</span>
          {overdue && <Badge tone="danger" size="sm" dot>Overdue {daysOverdue}d</Badge>}
          {issue.finePaise > 0 && <Badge tone="warning" size="sm">Fine ₹{(issue.finePaise / 100).toLocaleString('en-IN')}</Badge>}
        </div>
        <div className="text-xs text-slate-500 mt-0.5 flex items-center gap-3 flex-wrap">
          <span className="inline-flex items-center gap-1"><User size={11} /> {studentName}</span>
          <span className="inline-flex items-center gap-1"><Calendar size={11} /> Issued {new Date(issue.issuedAt).toLocaleDateString('en-IN')}</span>
          <span className={cn('inline-flex items-center gap-1', overdue && 'text-danger')}>
            <Clock size={11} /> Due {new Date(issue.dueDate).toLocaleDateString('en-IN')}
          </span>
        </div>
      </div>
      <RequireRole roles={OWNER_OR_ADMIN}>
        <Button variant="secondary" size="sm" onClick={onReturn} loading={busy}>
          <RotateCcw size={14} /> Return
        </Button>
      </RequireRole>
    </li>
  );
}

function IssueBookModal({
  open, onClose, tenantId, books,
}: {
  open: boolean; onClose: () => void; tenantId: string;
  books: BookDto[];
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const [bookId, setBookId] = useState('');
  const [studentId, setStudentId] = useState('');
  const [dueDate, setDueDate] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 14);
    return d.toISOString().slice(0, 10);
  });
  const [note, setNote] = useState('');

  const issue = useMutation({
    mutationFn: () => libraryApi.issue(tenantId, bookId, studentId, dueDate, note || undefined),
    onSuccess: () => {
      toast.success('Book issued');
      qc.invalidateQueries({ queryKey: ['library-issues', tenantId] });
      qc.invalidateQueries({ queryKey: ['library-books', tenantId] });
      onClose();
      setBookId(''); setStudentId(''); setNote('');
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not issue book'),
  });

  const availableBooks = books.filter((b) => b.availableCopies > 0);

  return (
    <Modal open={open} onClose={onClose} title="Issue a book">
      <form onSubmit={(e) => { e.preventDefault(); issue.mutate(); }} className="space-y-3">
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Book</span>
          <select
            value={bookId}
            onChange={(e) => setBookId(e.target.value)}
            required
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="">Select an available book</option>
            {availableBooks.map((b) => (
              <option key={b.id} value={b.id}>
                {b.title} {b.author ? `— ${b.author}` : ''} ({b.availableCopies} available)
              </option>
            ))}
          </select>
        </label>

        <StudentPicker
          tenantId={tenantId}
          label="Student"
          value={studentId || null}
          onChange={(id) => setStudentId(id ?? '')}
        />

        <Input type="date" label="Due date" required value={dueDate}
               onChange={(e) => setDueDate(e.target.value)}
               min={new Date().toISOString().slice(0, 10)} />

        <Input label="Note (optional)" value={note}
               onChange={(e) => setNote(e.target.value)} maxLength={200} />

        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={issue.isPending} disabled={!bookId || !studentId}>
            Issue book
          </Button>
        </div>
      </form>
    </Modal>
  );
}
