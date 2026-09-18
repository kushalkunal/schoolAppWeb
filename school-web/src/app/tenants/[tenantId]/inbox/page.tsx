'use client';

/**
 * Unified parent-message inbox. Shows every WA / email / SMS that went out, newest first.
 * Read-only view — sending is initiated from the source modules (circulars, payments, etc.).
 */
import { useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { inboxApi } from '@/api/endpoints/dailyOps';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Spinner } from '@/components/ui/Spinner';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { isFeatureEnabled } from '@/features/featureFlags';

const channelTone = (c: string) => c === 'WHATSAPP' ? 'success' : c === 'EMAIL' ? 'info' : 'neutral';

export default function InboxPage() {
  if (!isFeatureEnabled('UNIFIED_INBOX')) return <EmptyState title="Unified inbox is disabled" />;
  return <RequireRole roles={OWNER_OR_ADMIN}><Inner /></RequireRole>;
}

function Inner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const q = useQuery({ queryKey: ['inbox', tenantId], queryFn: () => inboxApi.list(tenantId, 0, 200) });

  const [channel, setChannel] = useState<'ALL' | 'WHATSAPP' | 'EMAIL' | 'SMS'>('ALL');
  const [search, setSearch] = useState('');

  const filtered = useMemo(() => {
    const all = q.data?.content ?? [];
    const needle = search.trim().toLowerCase();
    return all.filter((m) => {
      if (channel !== 'ALL' && m.channel !== channel) return false;
      if (!needle) return true;
      return (m.subject ?? '').toLowerCase().includes(needle)
        || m.body.toLowerCase().includes(needle)
        || (m.category ?? '').toLowerCase().includes(needle);
    });
  }, [q.data, channel, search]);

  return (
    <div className="space-y-4">
      <PageHeader title="Parent inbox" description="Every message sent to parents — WhatsApp and email." />
      <div className="flex flex-wrap items-center gap-2">
        {(['ALL', 'WHATSAPP', 'EMAIL', 'SMS'] as const).map((c) => (
          <button
            key={c}
            onClick={() => setChannel(c)}
            className={`px-3 py-1.5 rounded-brand text-xs font-medium border transition ${
              channel === c ? 'bg-primary text-white border-primary' : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
            }`}
          >
            {c === 'ALL' ? 'All' : c}
          </button>
        ))}
        <input
          type="search"
          placeholder="Search subject or body…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="ml-auto rounded-brand border border-slate-300 px-3 py-1.5 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition w-64"
        />
      </div>
      <Card><CardBody>
        {q.isLoading ? <Spinner /> : filtered.length === 0 ? (
          <p className="text-sm text-slate-500">No messages match.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {filtered.map(m => (
              <li key={m.id} className="py-3">
                <div className="flex items-center justify-between gap-2 mb-1">
                  <div className="flex items-center gap-2">
                    <Badge tone={channelTone(m.channel)} size="sm">{m.channel}</Badge>
                    <span className="text-xs text-slate-500">{m.category}</span>
                  </div>
                  <span className="text-xs text-slate-400">{new Date(m.sentAt).toLocaleString()}</span>
                </div>
                {m.subject && <div className="text-sm font-medium text-slate-900">{m.subject}</div>}
                <div className="text-sm text-slate-700 whitespace-pre-line">{m.body}</div>
                {m.mediaUrl && (
                  <a href={m.mediaUrl} target="_blank" rel="noreferrer" className="text-xs text-primary hover:underline">
                    📎 Attachment
                  </a>
                )}
              </li>
            ))}
          </ul>
        )}
      </CardBody></Card>
    </div>
  );
}
