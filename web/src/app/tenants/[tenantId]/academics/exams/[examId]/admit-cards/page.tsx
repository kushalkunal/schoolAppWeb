'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ChevronLeft,
  FileText,
  RefreshCw,
  Download,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Users,
  Zap,
  XCircle,
} from 'lucide-react';
import { academicsApi } from '@/api/endpoints/academics';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import type { AdmitCardResponse, AdmitCardStatus } from '@/types/domain';

const STATUS_TABS: { label: string; value: AdmitCardStatus | '' }[] = [
  { label: 'All', value: '' },
  { label: 'Generated', value: 'GENERATED' },
  { label: 'Downloaded', value: 'DOWNLOADED' },
  { label: 'Blocked', value: 'BLOCKED' },
  { label: 'Pending', value: 'PENDING' },
];

const STATUS_BADGE: Record<AdmitCardStatus, { label: string; cls: string }> = {
  GENERATED:  { label: 'Generated',  cls: 'bg-green-100 text-green-700' },
  DOWNLOADED: { label: 'Downloaded', cls: 'bg-blue-100 text-blue-700' },
  BLOCKED:    { label: 'Fee Due',    cls: 'bg-red-100 text-red-700' },
  PENDING:    { label: 'Pending',    cls: 'bg-yellow-100 text-yellow-700' },
};

function rupees(paise: number) {
  return `₹${(paise / 100).toLocaleString('en-IN', { minimumFractionDigits: 0 })}`;
}

export default function AdmitCardsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const examId   = typeof params.examId   === 'string' ? params.examId   : '';
  const qc = useQueryClient();

  const [statusFilter, setStatusFilter] = useState<AdmitCardStatus | ''>('');
  const [actionError, setActionError] = useState<string | null>(null);

  const statsQ = useQuery({
    queryKey: ['admit-card-stats', tenantId, examId],
    queryFn: () => academicsApi.getAdmitCardStats(tenantId, examId),
    enabled: !!tenantId && !!examId,
  });

  const cardsQ = useQuery({
    queryKey: ['admit-cards', tenantId, examId, statusFilter],
    queryFn: () => academicsApi.listAdmitCards(tenantId, examId, statusFilter || undefined),
    enabled: !!tenantId && !!examId,
  });

  const bulkGenerateMut = useMutation({
    mutationFn: () => academicsApi.bulkGenerateAdmitCards(tenantId, examId),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ['admit-card-stats', tenantId, examId] });
      qc.invalidateQueries({ queryKey: ['admit-cards', tenantId, examId] });
      setActionError(null);
      if (data.generated > 0) {
        alert(`${data.generated} admit card(s) generated successfully.`);
      } else {
        alert('All eligible cards are already generated.');
      }
    },
    onError: (e: Error) => setActionError(e.message),
  });

  const regenerateMut = useMutation({
    mutationFn: (studentId: string) => academicsApi.regenerateAdmitCard(tenantId, examId, studentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admit-card-stats', tenantId, examId] });
      qc.invalidateQueries({ queryKey: ['admit-cards', tenantId, examId] });
      setActionError(null);
    },
    onError: (e: Error) => setActionError(e.message),
  });

  const downloadMut = useMutation({
    mutationFn: (admitCardId: string) => academicsApi.markAdmitCardDownloaded(tenantId, examId, admitCardId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admit-cards', tenantId, examId] }),
  });

  const stats = statsQ.data;
  const cards = cardsQ.data ?? [];

  return (
    <div className="max-w-5xl mx-auto px-4 py-4 sm:py-6 space-y-5">
      {/* Back + Header */}
      <div className="flex items-center gap-2">
        <Link
          href={`/tenants/${tenantId}/academics/exams`}
          className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500"
        >
          <ChevronLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-lg sm:text-xl font-bold text-slate-900">Admit Cards</h1>
          <p className="text-xs text-slate-500">Hall ticket generation &amp; fee clearance status</p>
        </div>
      </div>

      {actionError && (
        <div role="alert" className="bg-red-50 border border-red-200 text-red-700 rounded px-4 py-3 text-sm">
          {actionError}
        </div>
      )}

      {/* Stats */}
      {stats ? (
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
          <StatCard icon={Users}        label="Total"      value={stats.total}      color="slate" />
          <StatCard icon={CheckCircle2} label="Generated"  value={stats.generated}  color="green" />
          <StatCard icon={Download}     label="Downloaded" value={stats.downloaded} color="blue" />
          <StatCard icon={XCircle}      label="Blocked"    value={stats.blocked}    color="red" />
          <StatCard icon={Clock}        label="Pending"    value={stats.pending}    color="yellow" />
        </div>
      ) : statsQ.isLoading ? (
        <div className="flex justify-center py-6"><Spinner /></div>
      ) : null}

      {/* Bulk Generate */}
      <RequireRole roles={OWNER_OR_ADMIN}>
        <Card className="p-4">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <h2 className="font-semibold text-slate-800">Bulk Generate</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Generates cards for all active students. Students with unpaid dues are automatically blocked.
                Cards auto-generate 4–5 days before exam start.
              </p>
            </div>
            <Button
              onClick={() => bulkGenerateMut.mutate()}
              loading={bulkGenerateMut.isPending}
              className="w-full sm:w-auto"
            >
              <Zap className="w-4 h-4" /> Generate All
            </Button>
          </div>
          {stats && stats.blocked > 0 && (
            <div className="mt-3 flex items-start gap-2 text-amber-700 bg-amber-50 rounded-lg p-3 text-sm">
              <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
              <span>
                <strong>{stats.blocked}</strong> student{stats.blocked > 1 ? 's are' : ' is'} blocked due to unpaid
                fees. Once fees are cleared, cards are regenerated automatically.
              </span>
            </div>
          )}
        </Card>
      </RequireRole>

      {/* Status tabs */}
      <div className="flex gap-1 overflow-x-auto scrollbar-none border-b border-slate-200 pb-0.5">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value}
            onClick={() => setStatusFilter(tab.value)}
            className={`px-3 py-1.5 rounded-t-md text-sm font-medium whitespace-nowrap transition-colors ${
              statusFilter === tab.value
                ? 'border-b-2 border-indigo-600 text-indigo-600'
                : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            {tab.label}
            {tab.value === 'BLOCKED' && stats?.blocked ? (
              <span className="ml-1.5 bg-red-100 text-red-700 text-xs rounded-full px-1.5">{stats.blocked}</span>
            ) : null}
          </button>
        ))}
      </div>

      {/* Cards list */}
      {cardsQ.isLoading ? (
        <div className="flex justify-center py-8"><Spinner /></div>
      ) : cardsQ.error ? (
        <ErrorBanner error={cardsQ.error} />
      ) : cards.length === 0 ? (
        <EmptyState statusFilter={statusFilter} />
      ) : (
        <>
          {/* Desktop table */}
          <div className="hidden sm:block overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs text-slate-500 uppercase tracking-wide">
                  <th className="pb-2 pr-4">Student</th>
                  <th className="pb-2 pr-4">Adm No.</th>
                  <th className="pb-2 pr-4">Class</th>
                  <th className="pb-2 pr-4">Seat No.</th>
                  <th className="pb-2 pr-4">Outstanding</th>
                  <th className="pb-2 pr-4">Status</th>
                  <th className="pb-2">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {cards.map((card) => (
                  <tr key={card.id} className={card.status === 'BLOCKED' ? 'bg-red-50/40' : ''}>
                    <td className="py-2.5 pr-4 font-medium text-slate-900">{card.studentName}</td>
                    <td className="py-2.5 pr-4 text-slate-500">{card.admissionNumber ?? '—'}</td>
                    <td className="py-2.5 pr-4 text-slate-500">
                      {[card.className, card.sectionName].filter(Boolean).join(' / ') || '—'}
                    </td>
                    <td className="py-2.5 pr-4 text-slate-500">{card.seatNumber ?? '—'}</td>
                    <td className="py-2.5 pr-4">
                      {card.feeCleared ? (
                        <span className="text-green-600 text-xs font-medium">Cleared</span>
                      ) : (
                        <span className="text-red-600 text-xs font-medium">{rupees(card.outstandingPaiseSnapshot)}</span>
                      )}
                    </td>
                    <td className="py-2.5 pr-4">
                      <StatusBadge status={card.status} />
                    </td>
                    <td className="py-2.5">
                      <CardActions
                        card={card}
                        onDownload={() => downloadMut.mutate(card.id)}
                        onRegenerate={() => regenerateMut.mutate(card.studentId)}
                        isPending={regenerateMut.isPending}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile card list */}
          <div className="sm:hidden space-y-3">
            {cards.map((card) => (
              <MobileCard
                key={card.id}
                card={card}
                onDownload={() => downloadMut.mutate(card.id)}
                onRegenerate={() => regenerateMut.mutate(card.studentId)}
                isPending={regenerateMut.isPending}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}

// ── Sub-components ────────────────────────────────────────────────────────────

function StatCard({
  icon: Icon,
  label,
  value,
  color,
}: {
  icon: React.ElementType;
  label: string;
  value: number;
  color: 'slate' | 'green' | 'blue' | 'red' | 'yellow';
}) {
  const colors = {
    slate:  'bg-slate-50  text-slate-700',
    green:  'bg-green-50  text-green-700',
    blue:   'bg-blue-50   text-blue-700',
    red:    'bg-red-50    text-red-700',
    yellow: 'bg-yellow-50 text-yellow-700',
  };
  return (
    <div className={`rounded-xl p-3 ${colors[color]} flex flex-col gap-1`}>
      <Icon className="w-4 h-4 opacity-70" />
      <div className="text-xl font-bold leading-none">{value}</div>
      <div className="text-xs opacity-70">{label}</div>
    </div>
  );
}

function StatusBadge({ status }: { status: AdmitCardStatus }) {
  const { label, cls } = STATUS_BADGE[status];
  return <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${cls}`}>{label}</span>;
}

function CardActions({
  card,
  onDownload,
  onRegenerate,
  isPending,
}: {
  card: AdmitCardResponse;
  onDownload: () => void;
  onRegenerate: () => void;
  isPending: boolean;
}) {
  const canDownload  = (card.status === 'GENERATED' || card.status === 'DOWNLOADED') && card.pdfUrl;
  const canRegenerate = card.status === 'BLOCKED' || card.status === 'PENDING' || card.status === 'GENERATED';

  return (
    <div className="flex items-center gap-1.5">
      {canDownload && (
        <a
          href={card.pdfUrl!}
          target="_blank"
          rel="noopener noreferrer"
          onClick={onDownload}
          className="inline-flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800 font-medium"
        >
          <Download className="w-3.5 h-3.5" />
          PDF
        </a>
      )}
      {canRegenerate && (
        <RequireRole roles={OWNER_OR_ADMIN}>
          <button
            onClick={onRegenerate}
            disabled={isPending}
            className="inline-flex items-center gap-1 text-xs text-slate-500 hover:text-slate-700 disabled:opacity-40"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isPending ? 'animate-spin' : ''}`} />
            {card.status === 'BLOCKED' ? 'Retry' : 'Regen'}
          </button>
        </RequireRole>
      )}
    </div>
  );
}

function MobileCard({
  card,
  onDownload,
  onRegenerate,
  isPending,
}: {
  card: AdmitCardResponse;
  onDownload: () => void;
  onRegenerate: () => void;
  isPending: boolean;
}) {
  const isBlocked = card.status === 'BLOCKED';
  return (
    <div className={`rounded-xl border p-4 space-y-2 ${isBlocked ? 'border-red-200 bg-red-50/30' : 'border-slate-200 bg-white'}`}>
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-semibold text-slate-900">{card.studentName}</p>
          <p className="text-xs text-slate-500">{card.admissionNumber ?? '—'}</p>
        </div>
        <StatusBadge status={card.status} />
      </div>

      <div className="grid grid-cols-2 gap-1 text-xs text-slate-600">
        <span className="text-slate-400">Class</span>
        <span>{[card.className, card.sectionName].filter(Boolean).join(' / ') || '—'}</span>
        <span className="text-slate-400">Seat No.</span>
        <span>{card.seatNumber ?? '—'}</span>
        <span className="text-slate-400">Fee Status</span>
        <span className={card.feeCleared ? 'text-green-600 font-medium' : 'text-red-600 font-medium'}>
          {card.feeCleared ? 'Cleared' : `Due: ${rupees(card.outstandingPaiseSnapshot)}`}
        </span>
      </div>

      {isBlocked && (
        <div className="flex items-center gap-1.5 text-xs text-red-600 bg-red-50 rounded-lg px-2 py-1.5">
          <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
          Card blocked — student has unpaid fees. Card auto-generates once fees are paid.
        </div>
      )}

      <div className="flex gap-2 pt-1">
        {(card.status === 'GENERATED' || card.status === 'DOWNLOADED') && card.pdfUrl && (
          <a
            href={card.pdfUrl!}
            target="_blank"
            rel="noopener noreferrer"
            onClick={onDownload}
            className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg bg-indigo-600 text-white text-sm font-medium min-h-[44px]"
          >
            <Download className="w-4 h-4" />
            Download PDF
          </a>
        )}
        <RequireRole roles={OWNER_OR_ADMIN}>
          {(card.status === 'BLOCKED' || card.status === 'PENDING' || card.status === 'GENERATED') && (
            <button
              onClick={onRegenerate}
              disabled={isPending}
              className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg border border-slate-200 text-slate-600 text-sm font-medium min-h-[44px] disabled:opacity-40"
            >
              <RefreshCw className={`w-4 h-4 ${isPending ? 'animate-spin' : ''}`} />
              {card.status === 'BLOCKED' ? 'Retry' : 'Regenerate'}
            </button>
          )}
        </RequireRole>
      </div>
    </div>
  );
}

function EmptyState({ statusFilter }: { statusFilter: string }) {
  return (
    <div className="flex flex-col items-center py-12 text-slate-400 text-sm space-y-2">
      <FileText className="w-10 h-10 opacity-30" />
      <p className="font-medium text-slate-500">No admit cards found</p>
      {statusFilter ? (
        <p>No cards with status "{statusFilter}"</p>
      ) : (
        <p>Click "Generate All" to create admit cards for this exam.</p>
      )}
    </div>
  );
}
