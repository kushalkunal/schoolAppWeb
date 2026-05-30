'use client';

/**
 * Message Templates — Slice 35.
 *
 * Lets OWNER/ADMIN customise per-school WhatsApp message bodies for known event types
 * (absence alert, fee receipt, fee reminder, fee overdue). Each card shows the
 * built-in default and the school's current override, with a live-preview textarea.
 *
 * Available placeholders are shown beneath each editor and validated by the backend's
 * {@link MessageTemplateService} at substitution time.
 */

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { MessageSquare, RotateCcw, Save } from 'lucide-react';
import { messageTemplatesApi, type MessageTemplate } from '@/api/endpoints/remindersAndTemplates';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { OWNER_OR_ADMIN, RequireRole, useHasRole } from '@/auth/RequireRole';

const PLACEHOLDERS: Record<string, string[]> = {
  ABSENCE_ALERT:         ['{parentName}', '{studentName}', '{date}', '{schoolName}'],
  FEE_RECEIPT:           ['{schoolName}', '{studentName}', '{receiptNumber}', '{date}', '{paymentMode}', '{amountPaid}', '{balance}'],
  PARENT_NOTIFY_FEE_DUE: ['{parentName}', '{studentName}', '{outstanding}', '{paymentLink}', '{schoolName}'],
  PARENT_NOTIFY_FEE_OVERDUE: ['{parentName}', '{studentName}', '{outstanding}', '{paymentLink}', '{schoolName}'],
};

const KEY_LABELS: Record<string, string> = {
  ABSENCE_ALERT:              'Absence Alert',
  FEE_RECEIPT:                'Fee Receipt',
  PARENT_NOTIFY_FEE_DUE:      'Fee Due Reminder',
  PARENT_NOTIFY_FEE_OVERDUE:  'Fee Overdue Reminder',
};

export default function MessageTemplatesPage() {
  return (
    <RequireRole roles={OWNER_OR_ADMIN}>
      <TemplatesContent />
    </RequireRole>
  );
}

function TemplatesContent() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const { showToast } = useToast();
  const canEdit = useHasRole(...OWNER_OR_ADMIN);

  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [editing, setEditing] = useState<Record<string, boolean>>({});

  const q = useQuery({
    queryKey: ['message-templates', tenantId],
    queryFn: () => messageTemplatesApi.list(tenantId),
    enabled: !!tenantId,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['message-templates', tenantId] });

  const saveMut = useMutation({
    mutationFn: ({ key, body }: { key: string; body: string }) =>
      messageTemplatesApi.upsert(tenantId, key, { templateKey: key, bodyTemplate: body }),
    onSuccess: (_, vars) => {
      invalidate();
      setEditing(e => ({ ...e, [vars.key]: false }));
      showToast('Template saved', 'success');
    },
    onError: () => showToast('Failed to save template', 'error'),
  });

  const resetMut = useMutation({
    mutationFn: (key: string) => messageTemplatesApi.reset(tenantId, key),
    onSuccess: (_, key) => {
      invalidate();
      setDrafts(d => { const n = { ...d }; delete n[key]; return n; });
      setEditing(e => ({ ...e, [key]: false }));
      showToast('Reset to default', 'success');
    },
    onError: () => showToast('Failed to reset template', 'error'),
  });

  function startEdit(tpl: MessageTemplate) {
    setDrafts(d => ({ ...d, [tpl.templateKey]: tpl.bodyTemplate ?? tpl.defaultBody }));
    setEditing(e => ({ ...e, [tpl.templateKey]: true }));
  }

  function cancelEdit(key: string) {
    setEditing(e => ({ ...e, [key]: false }));
  }

  if (q.isLoading) return <div className="flex items-center gap-2 text-slate-500"><Spinner />Loading…</div>;
  if (q.isError) return <ErrorBanner error={q.error} onRetry={q.refetch} />;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Message Templates"
        subtitle="Customise the WhatsApp messages your school sends to parents. Placeholders in {curly braces} are replaced automatically."
        icon={MessageSquare}
      />

      <div className="space-y-4 max-w-3xl">
        {q.data?.map(tpl => {
          const key = tpl.templateKey;
          const isEditing = editing[key] ?? false;
          const hasOverride = !!tpl.bodyTemplate;
          const currentDraft = drafts[key] ?? tpl.bodyTemplate ?? tpl.defaultBody;

          return (
            <Card key={key}>
              <CardHeader>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <CardTitle className="text-base">{KEY_LABELS[key] ?? key}</CardTitle>
                    <p className="text-xs text-slate-500 mt-0.5">Key: {key}</p>
                  </div>
                  <Badge tone={hasOverride ? 'primary' : 'neutral'} size="sm">
                    {hasOverride ? 'Custom' : 'Default'}
                  </Badge>
                </div>
              </CardHeader>
              <CardBody className="space-y-3">
                {isEditing ? (
                  <>
                    <textarea
                      rows={6}
                      className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm font-mono resize-y"
                      value={currentDraft}
                      onChange={e => setDrafts(d => ({ ...d, [key]: e.target.value }))}
                    />
                    <div className="flex flex-wrap gap-1">
                      {(PLACEHOLDERS[key] ?? []).map(ph => (
                        <code key={ph}
                          className="text-xs bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded cursor-pointer hover:bg-primary-soft"
                          onClick={() => setDrafts(d => ({ ...d, [key]: (d[key] ?? '') + ph }))}>
                          {ph}
                        </code>
                      ))}
                    </div>
                    <div className="flex gap-2 flex-wrap">
                      <Button size="sm" onClick={() => saveMut.mutate({ key, body: currentDraft })}
                        disabled={saveMut.isPending || !currentDraft.trim()}>
                        <Save size={13} className="mr-1" />Save
                      </Button>
                      {hasOverride && (
                        <Button size="sm" variant="ghost"
                          onClick={() => resetMut.mutate(key)}
                          disabled={resetMut.isPending}>
                          <RotateCcw size={13} className="mr-1" />Reset to default
                        </Button>
                      )}
                      <Button size="sm" variant="ghost" onClick={() => cancelEdit(key)}>Cancel</Button>
                    </div>
                  </>
                ) : (
                  <>
                    <pre className="text-xs text-slate-600 bg-slate-50 rounded-lg p-3 whitespace-pre-wrap font-sans leading-relaxed">
                      {tpl.bodyTemplate ?? tpl.defaultBody}
                    </pre>
                    {canEdit && (
                      <Button size="sm" variant="ghost" onClick={() => startEdit(tpl)}>
                        Edit template
                      </Button>
                    )}
                  </>
                )}
              </CardBody>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
