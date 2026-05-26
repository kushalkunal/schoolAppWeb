'use client';

import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { Printer } from 'lucide-react';
import { academicsApi } from '@/api/endpoints/academics';
import { studentsApi } from '@/api/endpoints/students';
import { schoolApi } from '@/api/endpoints/school';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';

/**
 * Printable report card. Tailwind's `print:` utility classes hide the action bar and
 * adjust the page to A4 when the user prints (Ctrl/Cmd-P or the Print button).
 */
export default function ReportCardPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const examId = typeof params.examId === 'string' ? params.examId : '';
  const studentId = typeof params.studentId === 'string' ? params.studentId : '';

  const card = useQuery({
    queryKey: ['report-card', tenantId, examId, studentId],
    queryFn: () => academicsApi.getStudentReportCard(tenantId, studentId, examId),
    enabled: !!tenantId && !!examId && !!studentId,
  });

  const student = useQuery({
    queryKey: ['student', tenantId, studentId],
    queryFn: () => studentsApi.get(tenantId, studentId),
    enabled: !!tenantId && !!studentId,
  });

  const school = useQuery({
    queryKey: ['school', tenantId],
    queryFn: () => schoolApi.get(tenantId),
    enabled: !!tenantId,
  });

  const exam = useQuery({
    queryKey: ['exams', tenantId],
    queryFn: () => academicsApi.listExams(tenantId),
    enabled: !!tenantId,
    select: (rows) => rows.find((e) => e.id === examId) ?? null,
  });

  if (card.isLoading || student.isLoading || school.isLoading) return <Spinner />;
  if (card.isError) return <ErrorBanner error={card.error} onRetry={() => card.refetch()} />;
  if (!card.data || !student.data || !school.data) return null;

  const c = card.data;
  const profile = student.data;
  const st = profile.student;
  const enr = profile.currentEnrollment;
  const sch = school.data;

  return (
    <div className="space-y-4 print:space-y-0">
      <div className="flex items-center justify-between print:hidden">
        <h1 className="text-2xl font-semibold">Report card</h1>
        <Button onClick={() => window.print()} variant="secondary">
          <Printer size={14} className="mr-1" /> Print
        </Button>
      </div>

      <Card className="max-w-3xl mx-auto print:border-0 print:shadow-none print:max-w-full">
        {/* Header */}
        <div className="text-center border-b border-slate-200 pb-4 mb-4">
          <h2 className="text-xl font-bold uppercase tracking-wide">{sch.name}</h2>
          <p className="text-sm text-slate-500">{sch.city}{sch.city && sch.state ? ', ' : ''}{sch.state}</p>
          <p className="text-sm font-medium text-slate-700 mt-2">
            {exam.data ? exam.data.name : 'Report Card'}
            {exam.data?.examType && (
              <span className="text-slate-400"> · {exam.data.examType.replace('_', ' ').toLowerCase()}</span>
            )}
          </p>
        </div>

        {/* Student info */}
        <div className="grid grid-cols-2 gap-4 mb-6 text-sm">
          <div>
            <div className="text-xs text-slate-500 uppercase">Student</div>
            <div className="font-medium">{st.displayName}</div>
          </div>
          <div>
            <div className="text-xs text-slate-500 uppercase">Admission No.</div>
            <div className="font-medium">{st.admissionNumber ?? '—'}</div>
          </div>
          <div>
            <div className="text-xs text-slate-500 uppercase">Class / Section</div>
            <div className="font-medium">{enr ? `${enr.className} ${enr.sectionName}` : '—'}</div>
          </div>
          <div>
            <div className="text-xs text-slate-500 uppercase">Roll No.</div>
            <div className="font-medium">{enr?.rollNumber ?? '—'}</div>
          </div>
        </div>

        {/* Summary */}
        <div className="grid grid-cols-4 gap-3 mb-6 text-center text-sm">
          <div className="p-3 bg-slate-50 rounded">
            <div className="text-xs text-slate-500 uppercase">Total</div>
            <div className="text-lg font-semibold">{c.obtainedMarks} / {c.totalMarks}</div>
          </div>
          <div className="p-3 bg-slate-50 rounded">
            <div className="text-xs text-slate-500 uppercase">Percentage</div>
            <div className="text-lg font-semibold">{c.percentage}%</div>
          </div>
          <div className="p-3 bg-slate-50 rounded">
            <div className="text-xs text-slate-500 uppercase">Grade</div>
            <div className="text-lg font-semibold">{c.grade ?? '—'}</div>
          </div>
          <div className="p-3 bg-slate-50 rounded">
            <div className="text-xs text-slate-500 uppercase">Class rank</div>
            <div className="text-lg font-semibold">{c.rankInClass ?? '—'}</div>
          </div>
        </div>

        {/* Subject breakdown lives in marks; here we show summary. Subject-by-subject view
            would require an additional endpoint that returns per-subject marks for one student.
            Backend currently returns the aggregate in ReportCardResponse — extend the API
            if you need per-subject rows on the printable card. */}
        <div className="text-xs text-slate-500 italic mb-4">
          Subject-by-subject breakdown is available in the Marks Entry sheet for this exam.
        </div>

        {/* Signatures */}
        <div className="grid grid-cols-3 gap-8 mt-12 text-xs text-slate-500 text-center">
          <div>
            <div className="border-t border-slate-300 pt-1">Class Teacher</div>
          </div>
          <div>
            <div className="border-t border-slate-300 pt-1">Principal</div>
          </div>
          <div>
            <div className="border-t border-slate-300 pt-1">Parent / Guardian</div>
          </div>
        </div>

        {c.waSentAt && (
          <div className="mt-6 text-xs text-slate-400 print:hidden">
            Sent to parents via WhatsApp on {new Date(c.waSentAt).toLocaleString()}.
          </div>
        )}
      </Card>
    </div>
  );
}
