'use client';

/**
 * Teachers → Timetable tab
 *
 * Grid: rows = periods, columns = Mon–Fri (days 1–5).
 * Each cell: click to assign a teacher + subject to that section-period-day slot.
 * Conflict detection is done server-side; errors surface inline.
 *
 * Workflow:
 *   1. Admin picks a section from the dropdown.
 *   2. Grid shows current timetable for that section.
 *   3. Click any cell to open the slot editor (teacher + subject + optional note).
 *   4. Submit → backend checks conflicts, saves, grid refreshes.
 */

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { AlertTriangle, Clock, Plus, Trash2 } from 'lucide-react';
import { timetableApi } from '@/api/endpoints/timetable';
import { schoolApi } from '@/api/endpoints/school';
import { academicsApi } from '@/api/endpoints/academics';
import { roomsApi, type Classroom } from '@/api/endpoints/rooms';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import type {
  ClassResponse,
  PeriodResponse,
  SectionResponse,
  StaffResponse,
  SubjectResponse,
  TimetableEntryResponse,
  UpsertTimetableEntryRequest,
} from '@/types/domain';

const DAYS = [
  { n: 1, label: 'Monday' },
  { n: 2, label: 'Tuesday' },
  { n: 3, label: 'Wednesday' },
  { n: 4, label: 'Thursday' },
  { n: 5, label: 'Friday' },
];

export default function TimetablePage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();

  const [sectionId, setSectionId] = useState('');
  const [addPeriodOpen, setAddPeriodOpen] = useState(false);
  const [slotEdit, setSlotEdit] = useState<{
    periodId: string;
    dayOfWeek: number;
    entry: TimetableEntryResponse | null;
  } | null>(null);

  // ---- Data fetching ----
  const classesQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });
  const periodsQ = useQuery({
    queryKey: ['timetable-periods', tenantId],
    queryFn: () => timetableApi.listPeriods(tenantId),
    enabled: !!tenantId,
  });
  const entriesQ = useQuery({
    queryKey: ['timetable-entries', tenantId, sectionId],
    queryFn: () => timetableApi.getSectionTimetable(tenantId, sectionId),
    enabled: !!tenantId && !!sectionId,
  });
  const staffQ = useQuery({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });
  const subjectsQ = useQuery({
    queryKey: ['subjects', tenantId],
    queryFn: () => academicsApi.listSubjects(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });
  const roomsQ = useQuery({
    queryKey: ['classrooms', tenantId],
    queryFn: () => roomsApi.list(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });

  const deletePeriod = useMutation({
    mutationFn: (periodId: string) => timetableApi.deletePeriod(tenantId, periodId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['timetable-periods', tenantId] }),
  });

  const deleteEntry = useMutation({
    mutationFn: (entryId: string) => timetableApi.deleteEntry(tenantId, entryId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['timetable-entries', tenantId, sectionId] }),
  });

  // Lookup maps
  const staffById = new Map<string, StaffResponse>(staffQ.data?.map((s) => [s.id, s]) ?? []);
  const subjectById = new Map<string, SubjectResponse>(subjectsQ.data?.map((s) => [s.id, s]) ?? []);

  // Flat section list for the picker
  const sections: { id: string; label: string }[] = [];
  classesQ.data?.forEach((c: ClassResponse) =>
    c.sections.forEach((s: SectionResponse) =>
      sections.push({ id: s.id, label: `Class ${c.name} – ${s.name}` }),
    ),
  );

  // Entry lookup: periodId + dayOfWeek → entry
  const entryKey = (periodId: string, day: number) => `${periodId}:${day}`;
  const entryMap = new Map<string, TimetableEntryResponse>();
  entriesQ.data?.forEach((e) => entryMap.set(entryKey(e.periodId, e.dayOfWeek), e));

  const periods = periodsQ.data ?? [];
  const teachers = staffQ.data?.filter(
    (s) => s.active && (s.role === 'CLASS_TEACHER' || s.role === 'SUBJECT_TEACHER'),
  ) ?? [];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap justify-between items-start gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Timetable</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Build the weekly period schedule. A teacher can only teach one class per period.
          </p>
        </div>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button variant="secondary" size="sm" onClick={() => setAddPeriodOpen(true)}>
            <Clock size={14} className="mr-1" /> Add period
          </Button>
        </RequireRole>
      </div>

      {/* Section picker */}
      <div className="flex items-center gap-3">
        <label className="text-sm font-medium text-slate-700 shrink-0">Section</label>
        <select
          className="rounded border border-slate-300 px-3 py-1.5 text-sm max-w-xs w-full"
          value={sectionId}
          onChange={(e) => setSectionId(e.target.value)}
        >
          <option value="">— pick a section —</option>
          {sections.map((s) => (
            <option key={s.id} value={s.id}>{s.label}</option>
          ))}
        </select>
      </div>

      {periodsQ.isLoading && <Spinner />}

      {/* Periods not set up yet */}
      {!periodsQ.isLoading && periods.length === 0 && (
        <div className="text-center py-10 bg-white border border-slate-200 rounded-lg text-slate-500">
          <Clock size={32} className="mx-auto mb-3 text-slate-300" />
          <p className="font-medium">No periods configured</p>
          <p className="text-sm mb-4">Add your school&apos;s daily periods first (e.g. Period 1 08:00–08:45).</p>
          <RequireRole roles={OWNER_OR_ADMIN}>
            <Button size="sm" onClick={() => setAddPeriodOpen(true)}>
              <Plus size={14} className="mr-1" /> Add first period
            </Button>
          </RequireRole>
        </div>
      )}

      {/* Period list */}
      {periods.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <div className="px-4 py-2 bg-slate-50 border-b border-slate-200 text-xs font-medium text-slate-600 uppercase">
            Periods
          </div>
          <div className="divide-y divide-slate-100">
            {periods.map((p) => (
              <div key={p.id} className="px-4 py-2 flex items-center justify-between text-sm">
                <div className="flex items-center gap-3">
                  <span className="font-medium">{p.name}</span>
                  {!p.breakSlot && (
                    <span className="text-slate-500 text-xs">
                      {p.startTime.slice(0, 5)} – {p.endTime.slice(0, 5)}
                    </span>
                  )}
                  {p.breakSlot && (
                    <span className="text-xs bg-amber-50 text-amber-700 px-1.5 py-0.5 rounded">Break</span>
                  )}
                </div>
                <RequireRole roles={OWNER_OR_ADMIN}>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => deletePeriod.mutate(p.id)}
                    disabled={deletePeriod.isPending}
                  >
                    <Trash2 size={13} className="text-danger" />
                  </Button>
                </RequireRole>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Weekly grid — only shown when a section is picked and there are periods */}
      {sectionId && periods.length > 0 && (
        <div className="space-y-2">
          <h2 className="text-base font-semibold">
            Weekly schedule — {sections.find((s) => s.id === sectionId)?.label}
          </h2>
          {entriesQ.isLoading && <Spinner />}
          {entriesQ.isError && <ErrorBanner error={entriesQ.error} onRetry={() => entriesQ.refetch()} />}

          <div className="overflow-x-auto">
            <table className="min-w-full text-sm border border-slate-200 rounded-lg overflow-hidden">
              <thead className="bg-slate-50">
                <tr>
                  <th className="px-3 py-2 text-left text-xs font-medium text-slate-600 uppercase border-b border-slate-200 w-28">
                    Period
                  </th>
                  {DAYS.map((d) => (
                    <th
                      key={d.n}
                      className="px-3 py-2 text-center text-xs font-medium text-slate-600 uppercase border-b border-slate-200"
                    >
                      {d.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-slate-100">
                {periods.filter((p) => !p.breakSlot).map((period) => (
                  <tr key={period.id}>
                    <td className="px-3 py-2 font-medium text-slate-700 border-r border-slate-100">
                      <div>{period.name}</div>
                      <div className="text-xs text-slate-400">
                        {period.startTime.slice(0, 5)} – {period.endTime.slice(0, 5)}
                      </div>
                    </td>
                    {DAYS.map((day) => {
                      const entry = entryMap.get(entryKey(period.id, day.n));
                      return (
                        <td
                          key={day.n}
                          className="px-2 py-1.5 text-center border-r border-slate-100 last:border-r-0 hover:bg-slate-50"
                        >
                          {entry ? (
                            <div className="space-y-0.5">
                              {entry.subjectId && (
                                <div className="text-xs font-semibold text-slate-800">
                                  {subjectById.get(entry.subjectId)?.name ?? '—'}
                                </div>
                              )}
                              {entry.teacherId && (
                                <div className="text-xs text-slate-500">
                                  {staffById.get(entry.teacherId)?.displayName ?? '—'}
                                </div>
                              )}
                              <RequireRole roles={OWNER_OR_ADMIN}>
                                <div className="flex justify-center gap-1 mt-1">
                                  <button
                                    className="text-xs text-primary hover:underline"
                                    onClick={() => setSlotEdit({ periodId: period.id, dayOfWeek: day.n, entry })}
                                  >
                                    Edit
                                  </button>
                                  {entry.id && (
                                    <button
                                      className="text-xs text-danger hover:underline"
                                      onClick={() => entry.id && deleteEntry.mutate(entry.id)}
                                    >
                                      Clear
                                    </button>
                                  )}
                                </div>
                              </RequireRole>
                            </div>
                          ) : (
                            <RequireRole roles={OWNER_OR_ADMIN}>
                              <button
                                className="text-xs text-slate-400 hover:text-primary hover:bg-primary-soft rounded px-2 py-1 transition"
                                onClick={() => setSlotEdit({ periodId: period.id, dayOfWeek: day.n, entry: null })}
                              >
                                + Assign
                              </button>
                            </RequireRole>
                          )}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Add period modal */}
      {addPeriodOpen && (
        <AddPeriodModal
          tenantId={tenantId}
          onClose={() => setAddPeriodOpen(false)}
          onSuccess={() => {
            setAddPeriodOpen(false);
            qc.invalidateQueries({ queryKey: ['timetable-periods', tenantId] });
          }}
        />
      )}

      {/* Slot editor modal */}
      {slotEdit && sectionId && (
        <SlotEditorModal
          tenantId={tenantId}
          sectionId={sectionId}
          periodId={slotEdit.periodId}
          dayOfWeek={slotEdit.dayOfWeek}
          entry={slotEdit.entry}
          teachers={teachers}
          subjects={subjectsQ.data ?? []}
          rooms={roomsQ.data ?? []}
          onClose={() => setSlotEdit(null)}
          onSuccess={() => {
            setSlotEdit(null);
            qc.invalidateQueries({ queryKey: ['timetable-entries', tenantId, sectionId] });
          }}
        />
      )}
    </div>
  );
}

// ---- Add Period Modal ----
function AddPeriodModal({
  tenantId, onClose, onSuccess,
}: { tenantId: string; onClose: () => void; onSuccess: () => void }) {
  const [name, setName] = useState('');
  const [startTime, setStartTime] = useState('08:00');
  const [endTime, setEndTime] = useState('08:45');
  const [isBreak, setIsBreak] = useState(false);

  const create = useMutation({
    mutationFn: () =>
      timetableApi.createPeriod(tenantId, {
        name: name.trim(),
        startTime: startTime + ':00',
        endTime: endTime + ':00',
        sortOrder: 0,
        breakSlot: isBreak,
      }),
    onSuccess,
  });

  return (
    <Modal open={true} onClose={onClose} title="Add period">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        {create.isError && <ErrorBanner error={create.error} />}
        <div>
          <label className="text-sm text-slate-700 mb-1 block">Period name</label>
          <input
            className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
            placeholder="e.g. Period 1 or Lunch Break"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </div>
        <label className="flex items-center gap-2 text-sm cursor-pointer">
          <input
            type="checkbox"
            checked={isBreak}
            onChange={(e) => setIsBreak(e.target.checked)}
            className="rounded"
          />
          <span>Break / lunch slot (not a teaching period)</span>
        </label>
        {!isBreak && (
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-sm text-slate-700 mb-1 block">Start time</label>
              <input
                type="time"
                className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
                required
              />
            </div>
            <div>
              <label className="text-sm text-slate-700 mb-1 block">End time</label>
              <input
                type="time"
                className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
                required
              />
            </div>
          </div>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={create.isPending}>Cancel</Button>
          <Button type="submit" disabled={create.isPending || !name}>
            {create.isPending ? 'Saving…' : 'Add period'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ---- Slot Editor Modal ----
function SlotEditorModal({
  tenantId, sectionId, periodId, dayOfWeek, entry,
  teachers, subjects, rooms, onClose, onSuccess,
}: {
  tenantId: string;
  sectionId: string;
  periodId: string;
  dayOfWeek: number;
  entry: TimetableEntryResponse | null;
  teachers: StaffResponse[];
  subjects: SubjectResponse[];
  rooms: Classroom[];
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [teacherId, setTeacherId] = useState(entry?.teacherId ?? '');
  const [subjectId, setSubjectId] = useState(entry?.subjectId ?? '');
  const [roomId, setRoomId] = useState(entry?.roomId ?? '');

  const save = useMutation({
    mutationFn: () => {
      const req: UpsertTimetableEntryRequest = {
        id: entry?.id ?? undefined,
        sectionId,
        periodId,
        dayOfWeek,
        teacherId: teacherId || undefined,
        subjectId: subjectId || undefined,
        roomId: roomId || undefined,
      };
      return timetableApi.upsertEntry(tenantId, req);
    },
    onSuccess,
  });

  const dayLabel = DAYS.find((d) => d.n === dayOfWeek)?.label ?? '';

  return (
    <Modal
      open={true}
      onClose={onClose}
      title={`${entry ? 'Edit' : 'Assign'} slot — ${dayLabel}`}
    >
      <form onSubmit={(e) => { e.preventDefault(); save.mutate(); }} className="space-y-3">
        {save.isError && (
          <div className="flex gap-2 items-start text-sm bg-red-50 border border-red-200 rounded px-3 py-2 text-red-700">
            <AlertTriangle size={14} className="mt-0.5 shrink-0" />
            <span>
              {save.error instanceof Error ? save.error.message : 'Could not save — teacher may already be busy in this slot.'}
            </span>
          </div>
        )}

        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Teacher</span>
          <select
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
            value={teacherId}
            onChange={(e) => setTeacherId(e.target.value)}
          >
            <option value="">— free period —</option>
            {teachers.map((t) => (
              <option key={t.id} value={t.id}>{t.displayName}</option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Subject</span>
          <select
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
            value={subjectId}
            onChange={(e) => setSubjectId(e.target.value)}
          >
            <option value="">— no subject —</option>
            {subjects.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Room</span>
          <select
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
            value={roomId}
            onChange={(e) => setRoomId(e.target.value)}
          >
            <option value="">— no room —</option>
            {rooms.map((r) => (
              <option key={r.id} value={r.id}>{r.name}{r.building ? ` · ${r.building}` : ''}</option>
            ))}
          </select>
        </label>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={save.isPending}>Cancel</Button>
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
