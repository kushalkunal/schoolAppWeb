'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ChevronLeft, Trophy, RefreshCw, Send, Download, BarChart2, CheckCircle2
} from 'lucide-react';
import { academicsApi } from '@/api/endpoints/academics';
import { schoolApi } from '@/api/endpoints/school';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { ATTENDANCE_WRITER, OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import type { ExamResultResponse } from '@/types/domain';

export default function ExamResultsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const examId = typeof params.examId === 'string' ? params.examId : '';
  const qc = useQueryClient();

  const [sectionId, setSectionId] = useState('');

  const classesQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
  });

  const dashboardQ = useQuery({
    queryKey: ['result-dashboard', tenantId, examId],
    queryFn: () => academicsApi.getResultDashboard(tenantId, examId),
    enabled: !!tenantId && !!examId,
  });

  const resultsQ = useQuery({
    queryKey: ['results', tenantId, examId, sectionId],
    queryFn: () => academicsApi.getResults(tenantId, examId, sectionId),
    enabled: !!sectionId,
  });

  const computeMutation = useMutation({
    mutationFn: () => academicsApi.computeResults(tenantId, examId, sectionId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['results', tenantId, examId, sectionId] });
      qc.invalidateQueries({ queryKey: ['result-dashboard', tenantId, examId] });
    },
  });

  const verifyMutation = useMutation({
    mutationFn: () => academicsApi.verifyResults(tenantId, examId, sectionId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['results', tenantId, examId, sectionId] });
      qc.invalidateQueries({ queryKey: ['result-dashboard', tenantId, examId] });
    },
  });

  const publishMutation = useMutation({
    mutationFn: () => academicsApi.publishResults(tenantId, examId, sectionId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['results', tenantId, examId, sectionId] });
      qc.invalidateQueries({ queryKey: ['result-dashboard', tenantId, examId] });
    },
  });

  const hasReady = resultsQ.data?.some(r => r.status === 'READY') ?? false;
  const hasVerified = resultsQ.data?.some(r => r.status === 'VERIFIED') ?? false;
  const allPublished = (resultsQ.data?.length ?? 0) > 0 && resultsQ.data!.every(r => r.status === 'PUBLISHED');

  function exportCsv(data: ExamResultResponse[]) {
    const header = 'Rank,Roll,Name,Admission No,Total Max,Obtained,Percentage,Grade,Pass/Fail\n';
    const rows = data.map(r =>
      [r.rankInSection, r.rollNumber ?? '', r.studentName, r.admissionNumber ?? '',
       r.totalMax, r.totalObtained, r.percentage, r.grade ?? '', r.pass ? 'Pass' : 'Fail'].join(',')
    ).join('\n');
    const blob = new Blob([header + rows], { type: 'text/csv' });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob);
    a.download = `results-${examId}-${sectionId}.csv`; a.click();
  }

  const dashboard = dashboardQ.data;

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Link href={`/tenants/${tenantId}/academics/exams`} className="text-slate-500 hover:text-slate-700">
            <ChevronLeft size={18} />
          </Link>
          <div>
            <h1 className="text-2xl font-semibold">Results &amp; Analytics</h1>
            <p className="text-sm text-slate-500">View computed results, rankings, and performance analytics.</p>
          </div>
        </div>
      </div>

      {/* Dashboard summary cards */}
      {dashboardQ.isLoading && <Spinner />}
      {dashboard && dashboard.sections.length > 0 && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {dashboard.sections.map(sec => (
            <Card key={sec.sectionId} className="text-center">
              <p className="text-xs text-slate-500 mb-1">{sec.sectionName}</p>
              <p className="text-2xl font-bold text-indigo-600">{sec.passPercentage.toFixed(1)}%</p>
              <p className="text-xs text-slate-400">Pass rate</p>
              <div className="flex justify-center gap-3 mt-2 text-xs">
                <span className="text-green-600 font-medium">{sec.passCount} pass</span>
                <span className="text-red-500 font-medium">{sec.failCount} fail</span>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Toppers row */}
      {dashboard && dashboard.sections.some(s => s.topper) && (
        <div>
          <h2 className="text-sm font-semibold text-slate-600 uppercase tracking-wide mb-2 flex items-center gap-1.5">
            <Trophy size={15} className="text-yellow-500" /> Section Toppers
          </h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {dashboard.sections.filter(s => s.topper).map(sec => (
              <Card key={sec.sectionId}>
                <p className="text-xs text-slate-500">{sec.sectionName}</p>
                <p className="font-semibold text-slate-800 mt-0.5">{sec.topper!.name}</p>
                <div className="flex items-center gap-2 mt-1">
                  <span className="text-lg font-bold text-indigo-600">{sec.topper!.percentage.toFixed(1)}%</span>
                  {sec.topper!.grade && (
                    <span className="text-xs bg-indigo-50 text-indigo-700 px-2 py-0.5 rounded-full font-medium">
                      {sec.topper!.grade}
                    </span>
                  )}
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Section-level results table */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-600 uppercase tracking-wide flex items-center gap-1.5">
            <BarChart2 size={15} /> Section Results
          </h2>
          <div className="flex items-center gap-2">
            {classesQ.data && (
              <select
                value={sectionId}
                onChange={e => setSectionId(e.target.value)}
                className="border border-slate-300 rounded px-3 py-1.5 text-sm"
              >
                <option value="">Select section</option>
                {classesQ.data.map(c => (
                  <optgroup key={c.id} label={c.name}>
                    {c.sections.map(s => (
                      <option key={s.id} value={s.id}>{c.name} - {s.name}</option>
                    ))}
                  </optgroup>
                ))}
              </select>
            )}
            {sectionId && (
              <>
                <RequireRole roles={OWNER_OR_ADMIN}>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => computeMutation.mutate()}
                    disabled={computeMutation.isPending}
                  >
                    <RefreshCw size={14} className="mr-1" />
                    {computeMutation.isPending ? 'Computing…' : 'Compute'}
                  </Button>
                </RequireRole>
                {/* Step 1: class teacher (or admin) verifies the computed results */}
                <RequireRole roles={ATTENDANCE_WRITER}>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => verifyMutation.mutate()}
                    disabled={verifyMutation.isPending || !hasReady}
                  >
                    <CheckCircle2 size={14} className="mr-1" />
                    {verifyMutation.isPending ? 'Verifying…' : 'Verify'}
                  </Button>
                </RequireRole>
                {/* Step 2: principal/admin publishes the verified results */}
                <RequireRole roles={OWNER_OR_ADMIN}>
                  <Button
                    size="sm"
                    onClick={() => publishMutation.mutate()}
                    disabled={publishMutation.isPending || !hasVerified}
                    title={!hasVerified ? 'Results must be verified by the class teacher first' : undefined}
                  >
                    <Send size={14} className="mr-1" />
                    {publishMutation.isPending ? 'Publishing…' : 'Publish & Notify'}
                  </Button>
                </RequireRole>
                {resultsQ.data && resultsQ.data.length > 0 && (
                  <Button variant="secondary" size="sm" onClick={() => exportCsv(resultsQ.data!)}>
                    <Download size={14} className="mr-1" /> CSV
                  </Button>
                )}
              </>
            )}
          </div>
        </div>

        {/* Flow hint: compute → verify (class teacher) → publish (principal) */}
        {sectionId && resultsQ.data && resultsQ.data.length > 0 && !allPublished && (
          <p className="text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded px-3 py-2">
            {hasVerified
              ? 'Results are verified. The Principal can now Publish & Notify to release report cards.'
              : hasReady
                ? 'Results are computed. The class teacher must Verify them, then the Principal can Publish.'
                : 'Compute results after all marks are submitted, then Verify and Publish.'}
          </p>
        )}
        {sectionId && allPublished && (
          <p className="text-xs text-green-700 bg-green-50 border border-green-200 rounded px-3 py-2">
            Results published — report cards generated and parents/teachers notified.
          </p>
        )}

        {computeMutation.isError && <ErrorBanner error={computeMutation.error} />}
        {verifyMutation.isError && <ErrorBanner error={verifyMutation.error} />}
        {publishMutation.isError && <ErrorBanner error={publishMutation.error} />}

        {resultsQ.isLoading && <Spinner />}
        {resultsQ.data && resultsQ.data.length === 0 && (
          <Card>
            <p className="text-center text-slate-500 py-6">
              No results computed yet. Click <strong>Compute</strong> after all marks are submitted.
            </p>
          </Card>
        )}

        {resultsQ.data && resultsQ.data.length > 0 && (
          <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
                <tr>
                  <th className="text-center px-3 py-2 font-medium w-12">Rank</th>
                  <th className="text-center px-3 py-2 font-medium w-12">Roll</th>
                  <th className="text-left px-4 py-2 font-medium">Student</th>
                  <th className="text-right px-4 py-2 font-medium">Obtained</th>
                  <th className="text-right px-4 py-2 font-medium">Total</th>
                  <th className="text-right px-4 py-2 font-medium">%</th>
                  <th className="text-center px-3 py-2 font-medium">Grade</th>
                  <th className="text-center px-3 py-2 font-medium">Result</th>
                  <th className="text-center px-3 py-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {resultsQ.data.map(r => (
                  <tr key={r.id} className="border-t border-slate-100 hover:bg-slate-50">
                    <td className="text-center px-3 py-2 font-bold text-indigo-700">{r.rankInSection ?? '—'}</td>
                    <td className="text-center px-3 py-2 text-slate-500">{r.rollNumber ?? '—'}</td>
                    <td className="px-4 py-2">
                      <p className="font-medium text-slate-800">{r.studentName}</p>
                      {r.admissionNumber && <p className="text-xs text-slate-400">{r.admissionNumber}</p>}
                    </td>
                    <td className="text-right px-4 py-2 font-medium">{r.totalObtained}</td>
                    <td className="text-right px-4 py-2 text-slate-500">{r.totalMax}</td>
                    <td className="text-right px-4 py-2 font-semibold">{r.percentage.toFixed(2)}%</td>
                    <td className="text-center px-3 py-2">
                      {r.grade ? (
                        <span className="bg-indigo-50 text-indigo-700 text-xs font-semibold px-2 py-0.5 rounded-full">
                          {r.grade}
                        </span>
                      ) : '—'}
                    </td>
                    <td className="text-center px-3 py-2">
                      {r.pass ? (
                        <span className="bg-green-50 text-green-700 text-xs font-semibold px-2 py-0.5 rounded-full">Pass</span>
                      ) : (
                        <span className="bg-red-50 text-red-600 text-xs font-semibold px-2 py-0.5 rounded-full">Fail</span>
                      )}
                    </td>
                    <td className="text-center px-3 py-2">
                      <StatusBadge status={r.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Subject averages */}
        {dashboard && sectionId && (() => {
          const sec = dashboard.sections.find(s => s.sectionId === sectionId);
          if (!sec || sec.subjectAverages.length === 0) return null;
          return (
            <Card>
              <h3 className="text-sm font-semibold text-slate-700 mb-3">Subject-wise Averages</h3>
              <div className="space-y-2">
                {sec.subjectAverages.map(s => {
                  const pct = s.maxMarks > 0 ? (s.averageObtained / s.maxMarks) * 100 : 0;
                  return (
                    <div key={s.subjectId} className="flex items-center gap-3">
                      <span className="text-sm text-slate-700 w-32 shrink-0">{s.subjectName}</span>
                      <div className="flex-1 bg-slate-100 rounded-full h-2">
                        <div
                          className="bg-indigo-500 h-2 rounded-full"
                          style={{ width: `${Math.min(pct, 100)}%` }}
                        />
                      </div>
                      <span className="text-sm font-medium text-slate-700 w-24 text-right">
                        {s.averageObtained.toFixed(1)} / {s.maxMarks}
                      </span>
                    </div>
                  );
                })}
              </div>
            </Card>
          );
        })()}
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { label: string; cls: string }> = {
    DRAFT: { label: 'Draft', cls: 'bg-slate-100 text-slate-500' },
    READY: { label: 'Ready', cls: 'bg-blue-50 text-blue-600' },
    VERIFIED: { label: 'Verified', cls: 'bg-indigo-50 text-indigo-600' },
    PUBLISHED: { label: 'Published', cls: 'bg-green-50 text-green-700' },
  };
  const s = map[status] ?? { label: status, cls: 'bg-slate-100 text-slate-500' };
  return (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${s.cls}`}>{s.label}</span>
  );
}
