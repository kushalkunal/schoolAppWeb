'use client';

/**
 * Notification Logs — Slice 35.
 *
 * Shows OWNER/ADMIN every outbound WhatsApp message with its delivery status.
 * Staff can filter by status or event type, and re-send any message with one click.
 *
 * Status lifecycle: QUEUED → SENT → DELIVERED → READ (or FAILED at any step).
 * The "Re-send" action dispatches a new message (new log row) with the original body;
 * it does not mutate the original row so the audit trail stays intact.
 */

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Bell, RefreshCw, RotateCcw } from 'lucide-react';
import { notificationsApi, type NotificationLogEntry, type NotificationStatus } from '@/api/endpoints/notifications';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { formatDate } from '@/lib/utils';

const STATUS_TONE: Record<NotificationStatus, 'success' | 'warning' | 'error' | 'neutral'> = {
  READ:      'success',
  DELIVERED: 'success',
  SENT:      'warning',
  QUEUED:    'neutral',
  FAILED:    'error',
};

const EVENT_TYPE_LABELS: Record<string, string> = {
  ABSENCE_ALERT:              'Absence Alert',
  LATE_ARRIVAL_ALERT:         'Late Arrival',
  FEE_RECEIPT:                'Fee Receipt',
  FEE_REMINDER:               'Fee Reminder',
  PARENT_NOTIFY_FEE_DUE:      'Fee Due Reminder',
  PARENT_NOTIFY_FEE_OVERDUE:  'Fee Overdue',
  CIRCULAR:                   'Circular',
  REPORT_CARD:                'Report Card',
  OTP:                        'OTP',
};

export default function NotificationLogsPage() {
  return (
    <RequireRole roles={OWNER_OR_ADMIN}>
      <LogsContent />
    </RequireRole>
  );
}

function LogsContent() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const { showToast } = useToast();
  const qc = useQueryClient();

  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [expanded, setExpanded] = useState<string | null>(null);

  const q = useQuery({
    queryKey: ['notification-logs', tenantId, page, statusFilter, typeFilter],
    queryFn: () => notificationsApi.list(tenantId, {
      page,
      size: 20,
      status: statusFilter || undefined,
      eventType: typeFilter || undefined,
    }),
    enabled: !!tenantId,
    staleTime: 30_000,
  });

  const resendMut = useMutation({
    mutationFn: (id: string) => notificationsApi.resend(tenantId, id),
    onSuccess: () => {
      showToast('Message re-sent', 'success');
      qc.invalidateQueries({ queryKey: ['notification-logs', tenantId] });
    },
    onError: () => showToast('Failed to re-send', 'error'),
  });

  const totalPages = q.data ? Math.ceil(q.data.total / q.data.size) : 0;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Notification Logs"
        subtitle="Delivery history for every outbound WhatsApp message. Use Re-send if a parent reports they didn't receive it."
        icon={Bell}
      />

      {/* Filters */}
      <div className="flex flex-wrap gap-3 items-center">
        <select
          className="border border-slate-200 rounded-lg px-3 py-2 text-sm"
          value={statusFilter}
          onChange={e => { setStatusFilter(e.target.value); setPage(0); }}
        >
          <option value="">All statuses</option>
          {(['QUEUED', 'SENT', 'DELIVERED', 'READ', 'FAILED'] as NotificationStatus[]).map(s => (
            <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>
          ))}
        </select>
        <select
          className="border border-slate-200 rounded-lg px-3 py-2 text-sm"
          value={typeFilter}
          onChange={e => { setTypeFilter(e.target.value); setPage(0); }}
        >
          <option value="">All types</option>
          {Object.entries(EVENT_TYPE_LABELS).map(([k, v]) => (
            <option key={k} value={k}>{v}</option>
          ))}
        </select>
        <Button variant="ghost" size="sm"
          onClick={() => qc.invalidateQueries({ queryKey: ['notification-logs', tenantId] })}>
          <RefreshCw size={14} className="mr-1" />Refresh
        </Button>
        {q.data && (
          <span className="text-sm text-slate-500 ml-auto">
            {q.data.total.toLocaleString()} message{q.data.total !== 1 ? 's' : ''}
          </span>
        )}
      </div>

      {q.isLoading && <div className="flex items-center gap-2 text-slate-500"><Spinner />Loading…</div>}
      {q.isError && <ErrorBanner error={q.error} onRetry={q.refetch} />}

      {q.data && q.data.items.length === 0 && (
        <div className="text-slate-500 text-sm py-10 text-center">No messages found.</div>
      )}

      {q.data && q.data.items.length > 0 && (
        <Card>
          <CardBody className="p-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-100">
                <tr className="text-left text-xs text-slate-500 uppercase tracking-wide">
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Recipient</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Sent at</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {q.data.items.map(log => (
                  <>
                    <tr key={log.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3">
                        <span className="font-medium">{EVENT_TYPE_LABELS[log.eventType] ?? log.eventType}</span>
                      </td>
                      <td className="px-4 py-3">
                        <div>{log.recipientName ?? '—'}</div>
                        <div className="text-xs text-slate-400">{log.recipientPhone}</div>
                      </td>
                      <td className="px-4 py-3">
                        <Badge tone={STATUS_TONE[log.status]} size="sm">
                          {log.status.charAt(0) + log.status.slice(1).toLowerCase()}
                        </Badge>
                        {log.errorMessage && (
                          <div className="text-xs text-red-500 mt-0.5 max-w-[200px] truncate" title={log.errorMessage}>
                            {log.errorMessage}
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-slate-500 text-xs">
                        {log.sentAt ? formatDate(log.sentAt) : formatDate(log.createdAt)}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            className="text-xs text-slate-400 hover:text-slate-700"
                            onClick={() => setExpanded(expanded === log.id ? null : log.id)}
                          >
                            {expanded === log.id ? 'Hide' : 'Preview'}
                          </button>
                          <Button size="sm" variant="ghost"
                            disabled={resendMut.isPending}
                            onClick={() => resendMut.mutate(log.id)}>
                            <RotateCcw size={13} className="mr-1" />Re-send
                          </Button>
                        </div>
                      </td>
                    </tr>
                    {expanded === log.id && log.messageBody && (
                      <tr key={`${log.id}-body`}>
                        <td colSpan={5} className="px-4 pb-3 pt-0">
                          <pre className="text-xs bg-slate-50 rounded-lg p-3 whitespace-pre-wrap font-sans text-slate-600 leading-relaxed">
                            {log.messageBody}
                          </pre>
                        </td>
                      </tr>
                    )}
                  </>
                ))}
              </tbody>
            </table>
          </CardBody>
        </Card>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
            ← Previous
          </Button>
          <span className="text-slate-500">Page {page + 1} of {totalPages}</span>
          <Button variant="ghost" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
            Next →
          </Button>
        </div>
      )}
    </div>
  );
}
