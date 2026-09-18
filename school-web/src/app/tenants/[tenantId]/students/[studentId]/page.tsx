'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { studentsApi } from '@/api/endpoints/students';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';

export default function StudentDetailPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const studentId = typeof params.studentId === 'string' ? params.studentId : '';

  const q = useQuery({
    queryKey: ['student-profile', tenantId, studentId],
    queryFn: () => studentsApi.get(tenantId, studentId),
    enabled: !!tenantId && !!studentId,
  });

  if (q.isLoading) {
    return <div className="text-slate-500 flex items-center gap-2"><Spinner /> Loading…</div>;
  }
  if (q.isError) {
    return <ErrorBanner error={q.error} onRetry={() => q.refetch()} />;
  }

  const p = q.data!;
  return (
    <div className="space-y-4">
      <div>
        <Link href={`/tenants/${tenantId}/students`} className="text-sm text-slate-500 hover:underline">
          ← All students
        </Link>
        <h1 className="text-2xl font-semibold mt-1">{p.student.displayName}</h1>
        {p.currentEnrollment && (
          <p className="text-sm text-slate-500">
            {p.currentEnrollment.className} · {p.currentEnrollment.sectionName}
            {p.currentEnrollment.rollNumber ? ` · Roll ${p.currentEnrollment.rollNumber}` : ''}
            {' '}— {p.currentEnrollment.academicYearName}
          </p>
        )}
      </div>

      <div className="flex flex-wrap gap-2">
        <Link href={`/tenants/${tenantId}/fees/dashboard`} className="px-3 py-1.5 rounded-brand bg-white border border-slate-200 text-xs font-medium hover:bg-slate-50">💰 Fees</Link>
        {p.currentEnrollment && (
          <Link href={`/tenants/${tenantId}/attendance/${p.currentEnrollment.sectionId}`} className="px-3 py-1.5 rounded-brand bg-white border border-slate-200 text-xs font-medium hover:bg-slate-50">📅 Attendance</Link>
        )}
        <Link href={`/tenants/${tenantId}/incidents`} className="px-3 py-1.5 rounded-brand bg-white border border-slate-200 text-xs font-medium hover:bg-slate-50">⚠️ Incidents</Link>
        <Link href={`/tenants/${tenantId}/homework`} className="px-3 py-1.5 rounded-brand bg-white border border-slate-200 text-xs font-medium hover:bg-slate-50">📚 Homework</Link>
        <Link href={`/tenants/${tenantId}/risk`} className="px-3 py-1.5 rounded-brand bg-white border border-slate-200 text-xs font-medium hover:bg-slate-50">📊 Risk</Link>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader><CardTitle>Profile</CardTitle></CardHeader>
          <CardBody className="space-y-2 text-sm">
            <KV label="Admission no.">{p.student.admissionNumber ?? '—'}</KV>
            <KV label="Gender">{p.student.gender ?? '—'}</KV>
            <KV label="Date of birth">{p.student.dateOfBirth ?? '—'}</KV>
            <KV label="Blood group">{p.student.bloodGroup ?? '—'}</KV>
            <KV label="Status">
              {p.student.active
                ? <span className="text-green-700">Active</span>
                : <span className="text-slate-500">Inactive</span>}
            </KV>
          </CardBody>
        </Card>

        <Card>
          <CardHeader><CardTitle>Parents</CardTitle></CardHeader>
          <CardBody>
            {p.parents.length === 0 ? (
              <p className="text-sm text-slate-500">No parents linked.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {p.parents.map((par) => (
                  <li key={par.id} className="flex justify-between border-b border-slate-100 pb-2 last:border-b-0">
                    <div>
                      <div className="font-medium">{par.name ?? '—'}</div>
                      <div className="text-slate-500 text-xs">{par.relation ?? 'GUARDIAN'}</div>
                    </div>
                    <div className="text-right text-slate-600 text-xs">
                      <div>{par.phone ?? '—'}</div>
                      {par.primary && <span className="inline-block mt-1 bg-primary text-white text-[10px] px-2 py-0.5 rounded">Primary</span>}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle>Siblings</CardTitle></CardHeader>
        <CardBody>
          {p.siblings.length === 0 ? (
            <p className="text-sm text-slate-500">No siblings at this school.</p>
          ) : (
            <ul className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 text-sm">
              {p.siblings.map((s) => (
                <li key={s.id}>
                  <Link href={`/tenants/${tenantId}/students/${s.id}`} className="block p-2 border border-slate-200 rounded hover:bg-slate-50">
                    <div className="font-medium">{s.displayName}</div>
                    <div className="text-xs text-slate-500">{s.admissionNumber ?? '—'}</div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </CardBody>
      </Card>
    </div>
  );
}

function KV({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex justify-between">
      <span className="text-slate-500">{label}</span>
      <span className="text-slate-900">{children}</span>
    </div>
  );
}
