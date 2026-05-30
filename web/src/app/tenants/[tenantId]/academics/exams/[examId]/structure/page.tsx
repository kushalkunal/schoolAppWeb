'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, Plus, Trash2, BookOpen, CheckCircle2 } from 'lucide-react';
import { academicsApi } from '@/api/endpoints/academics';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import type { ConfigureExamStructureRequest, SubjectResponse } from '@/types/domain';

/** Quick presets to make configuration fast for common school patterns. */
const PRESETS = [
  { label: 'Theory only (100)', components: [{ componentName: 'Theory', maxMarks: 100, passingMarks: 33, sortOrder: 1 }] },
  { label: 'Theory 80 + Practical 20', components: [{ componentName: 'Theory', maxMarks: 80, passingMarks: 26, sortOrder: 1 }, { componentName: 'Practical', maxMarks: 20, passingMarks: 7, sortOrder: 2 }] },
  { label: 'Theory 70 + Practical 30', components: [{ componentName: 'Theory', maxMarks: 70, passingMarks: 23, sortOrder: 1 }, { componentName: 'Practical', maxMarks: 30, passingMarks: 10, sortOrder: 2 }] },
  { label: 'Theory 50 + Internal 30 + Practical 20', components: [{ componentName: 'Theory', maxMarks: 50, passingMarks: 17, sortOrder: 1 }, { componentName: 'Internal', maxMarks: 30, passingMarks: 10, sortOrder: 2 }, { componentName: 'Practical', maxMarks: 20, passingMarks: 7, sortOrder: 3 }] },
] as const;

type ComponentDraft = { componentName: string; maxMarks: string; passingMarks: string; sortOrder: number };

export default function ExamStructurePage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const examId = typeof params.examId === 'string' ? params.examId : '';
  const qc = useQueryClient();

  const [modalOpen, setModalOpen] = useState(false);
  const [selectedSubjectId, setSelectedSubjectId] = useState('');
  const [components, setComponents] = useState<ComponentDraft[]>([
    { componentName: 'Theory', maxMarks: '100', passingMarks: '33', sortOrder: 1 },
  ]);

  const structureQ = useQuery({
    queryKey: ['exam-structure', tenantId, examId],
    queryFn: () => academicsApi.getExamStructure(tenantId, examId),
    enabled: !!tenantId && !!examId,
  });

  const subjectsQ = useQuery({
    queryKey: ['subjects', tenantId],
    queryFn: () => academicsApi.listSubjects(tenantId),
    enabled: !!tenantId,
  });

  const configureMutation = useMutation({
    mutationFn: (req: ConfigureExamStructureRequest) =>
      academicsApi.configureExamStructure(tenantId, examId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['exam-structure', tenantId, examId] });
      setModalOpen(false);
    },
  });

  function openFor(subjectId: string) {
    setSelectedSubjectId(subjectId);
    const existing = structureQ.data?.find(s => s.subjectId === subjectId);
    if (existing) {
      setComponents(existing.components.map(c => ({
        componentName: c.componentName,
        maxMarks: String(c.maxMarks),
        passingMarks: c.passingMarks != null ? String(c.passingMarks) : '',
        sortOrder: c.sortOrder,
      })));
    } else {
      setComponents([{ componentName: 'Theory', maxMarks: '100', passingMarks: '33', sortOrder: 1 }]);
    }
    setModalOpen(true);
  }

  function applyPreset(preset: typeof PRESETS[number]) {
    setComponents(preset.components.map(c => ({
      componentName: c.componentName,
      maxMarks: String(c.maxMarks),
      passingMarks: String(c.passingMarks),
      sortOrder: c.sortOrder,
    })));
  }

  function addComponent() {
    setComponents(prev => [
      ...prev,
      { componentName: '', maxMarks: '', passingMarks: '', sortOrder: prev.length + 1 },
    ]);
  }

  function removeComponent(i: number) {
    setComponents(prev => prev.filter((_, idx) => idx !== i).map((c, idx) => ({ ...c, sortOrder: idx + 1 })));
  }

  function updateComponent(i: number, field: keyof ComponentDraft, value: string) {
    setComponents(prev => prev.map((c, idx) => idx === i ? { ...c, [field]: value } : c));
  }

  function handleSave() {
    const req: ConfigureExamStructureRequest = {
      subjectId: selectedSubjectId,
      components: components.map((c, i) => ({
        componentName: c.componentName.trim(),
        maxMarks: parseFloat(c.maxMarks),
        passingMarks: c.passingMarks ? parseFloat(c.passingMarks) : undefined,
        sortOrder: i + 1,
      })).filter(c => c.componentName && c.maxMarks > 0),
    };
    configureMutation.mutate(req);
  }

  const totalMax = components.reduce((sum, c) => sum + (parseFloat(c.maxMarks) || 0), 0);
  const configuredSubjectIds = new Set(structureQ.data?.map(s => s.subjectId) ?? []);
  const selectedSubject = subjectsQ.data?.find(s => s.id === selectedSubjectId);

  return (
    <div className="space-y-5 max-w-4xl">
      <div className="flex items-center gap-3">
        <Link href={`/tenants/${tenantId}/academics/exams`} className="text-slate-500 hover:text-slate-700">
          <ChevronLeft size={18} />
        </Link>
        <div>
          <h1 className="text-2xl font-semibold">Exam Structure</h1>
          <p className="text-sm text-slate-500">Configure which components (Theory, Practical, etc.) each subject has and their maximum marks.</p>
        </div>
      </div>

      {structureQ.isLoading && <Spinner />}
      {structureQ.isError && <ErrorBanner error={structureQ.error} onRetry={() => structureQ.refetch()} />}

      {subjectsQ.data && (
        <div className="space-y-3">
          {subjectsQ.data.length === 0 ? (
            <Card>
              <p className="text-slate-500 text-center py-6">No subjects found. Add subjects first from the Subjects page.</p>
            </Card>
          ) : (
            subjectsQ.data.map(subject => {
              const existing = structureQ.data?.find(s => s.subjectId === subject.id);
              return (
                <Card key={subject.id}>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <BookOpen size={18} className="text-slate-400" />
                      <div>
                        <p className="font-medium text-slate-800">{subject.name}</p>
                        {existing ? (
                          <p className="text-xs text-slate-500 mt-0.5">
                            {existing.components.map(c => `${c.componentName} (${c.maxMarks})`).join(' + ')}
                            {' '}<span className="font-medium">= {existing.totalMax} marks</span>
                          </p>
                        ) : (
                          <p className="text-xs text-amber-600 mt-0.5">Not configured</p>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      {existing && <CheckCircle2 size={16} className="text-green-500" />}
                      <RequireRole roles={OWNER_OR_ADMIN}>
                        <Button variant="secondary" size="sm" onClick={() => openFor(subject.id)}>
                          {existing ? 'Edit' : 'Configure'}
                        </Button>
                      </RequireRole>
                    </div>
                  </div>
                </Card>
              );
            })
          )}
        </div>
      )}

      {/* Configure modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={`Configure: ${selectedSubject?.name ?? ''}`}
      >
        <div className="space-y-4">
          {/* Quick presets */}
          <div>
            <p className="text-xs font-medium text-slate-600 mb-2">Quick presets</p>
            <div className="flex flex-wrap gap-2">
              {PRESETS.map(p => (
                <button
                  key={p.label}
                  onClick={() => applyPreset(p)}
                  className="text-xs px-3 py-1.5 rounded-full border border-indigo-200 text-indigo-700 hover:bg-indigo-50 transition-colors"
                >
                  {p.label}
                </button>
              ))}
            </div>
          </div>

          {/* Components table */}
          <div>
            <div className="grid grid-cols-[1fr_110px_110px_32px] gap-2 text-xs font-medium text-slate-500 mb-1 px-1">
              <span>Component</span><span>Max marks</span><span>Passing marks</span><span />
            </div>
            {components.map((c, i) => (
              <div key={i} className="grid grid-cols-[1fr_110px_110px_32px] gap-2 mb-2">
                <Input
                  value={c.componentName}
                  placeholder="e.g. Theory"
                  onChange={e => updateComponent(i, 'componentName', e.target.value)}
                />
                <Input
                  type="number"
                  value={c.maxMarks}
                  placeholder="100"
                  min={0}
                  onChange={e => updateComponent(i, 'maxMarks', e.target.value)}
                />
                <Input
                  type="number"
                  value={c.passingMarks}
                  placeholder="33"
                  min={0}
                  onChange={e => updateComponent(i, 'passingMarks', e.target.value)}
                />
                <button
                  onClick={() => removeComponent(i)}
                  disabled={components.length === 1}
                  className="text-slate-400 hover:text-red-500 disabled:opacity-30 transition-colors"
                >
                  <Trash2 size={15} />
                </button>
              </div>
            ))}
            <button
              onClick={addComponent}
              className="flex items-center gap-1 text-sm text-indigo-600 hover:text-indigo-800 mt-1"
            >
              <Plus size={14} /> Add component
            </button>
          </div>

          <div className="flex items-center justify-between pt-2 border-t border-slate-100">
            <p className="text-sm font-medium text-slate-700">Total: {totalMax} marks</p>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
              <Button
                onClick={handleSave}
                disabled={configureMutation.isPending || components.some(c => !c.componentName || !c.maxMarks)}
              >
                {configureMutation.isPending ? 'Saving…' : 'Save structure'}
              </Button>
            </div>
          </div>
          {configureMutation.isError && <ErrorBanner error={configureMutation.error} />}
        </div>
      </Modal>
    </div>
  );
}
