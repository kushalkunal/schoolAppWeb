# `academics` module

Subjects, exams, grid-style marks entry, report-card generation + delivery, teacher-subject assignments, and attendance-based exam eligibility.

**Package:** `in.schoolapp.academics`

---

## Entities

| Entity | Table | Notes |
|---|---|---|
| [`Subject`](../../backend/src/main/java/in/schoolapp/academics/entity/Subject.java) | `subjects` | Unique per `(school_id, name)`. |
| [`Exam`](../../backend/src/main/java/in/schoolapp/academics/entity/Exam.java) | `exams` | Scoped to an academic year. `is_published=true` finalises the marks and unlocks report-card generation. |
| [`ExamMark`](../../backend/src/main/java/in/schoolapp/academics/entity/ExamMark.java) | `exam_marks` | Unique per `(exam_id, student_id, subject_id)` — the grid is an upsert. `is_draft=true` until the teacher submits final. `grade` is auto-derived from percentage via `GradeCalculator`. |
| [`ReportCard`](../../backend/src/main/java/in/schoolapp/academics/entity/ReportCard.java) | `report_cards` | Unique per `(student_id, exam_id)`; regeneration updates the same row. Carries `wa_sent_at` / `wa_delivered_at` / `wa_read_at` for delivery telemetry. |
| [`TeacherSubjectAssignment`](../../backend/src/main/java/in/schoolapp/academics/entity/TeacherSubjectAssignment.java) | `teacher_subject_assignments` | Unique per `(staff_id, subject_id, section_id, academic_year_id)`. Who teaches what. |
| [`ExamType`](../../backend/src/main/java/in/schoolapp/academics/entity/ExamType.java) | enum | `UNIT_TEST`, `TERM`, `ANNUAL`, `MOCK`, `ACTIVITY` |

---

## Services

### [`SubjectService`](../../backend/src/main/java/in/schoolapp/academics/SubjectService.java)
- `bulkCreate(tenantId, req)` — idempotent; existing names (case-sensitive) are returned as-is, not errored.
- `listSubjects(tenantId)` — ordered by name.
- `getSubjectOrThrow(tenantId, subjectId)` — tenant-boundary helper.

### [`ExamService`](../../backend/src/main/java/in/schoolapp/academics/ExamService.java)
- `createExam(tenantId, req)` — pinned to the current academic year.
- `listCurrentYearExams(tenantId)` — ordered by start date desc.
- `publishExam(tenantId, examId)` — flips `isPublished=true`, **audit-logs** the `PUBLISH` action via `AuditLogger.logAction`. Publishing is what locks marks and permits report-card generation.
- `getExamOrThrow(tenantId, examId)` — package-private helper.

### [`MarksService`](../../backend/src/main/java/in/schoolapp/academics/MarksService.java)
- `submitBulk(tenantId, examId, req)` — the grid submit. Refuses writes if the exam is published. Validates every submitted `subjectId` belongs to the tenant (prevents cross-tenant subject injection via a stolen JWT). Per entry: non-negative check, max-marks check, upsert by `(exam, student, subject)`, derive grade from `GradeCalculator` + the school's board, persist with `is_draft = !submitFinal`.
- `getEntrySheet(tenantId, examId, sectionId)` — returns roster rows (sorted by roll number, then name) + any existing marks — the grid the teacher sees.
- `getCompletionStatus(tenantId, examId, sectionId)` — per-subject count of entered marks vs roster, plus a `complete` flag.
- `createHistorical(...)` — migration commit path. Treats marks as finalised (`is_draft=false`), does **not** check `exam.isPublished()` — historical marks predate publish semantics.

### [`GradeCalculator`](../../backend/src/main/java/in/schoolapp/academics/GradeCalculator.java)
Board-specific grade bands (CBSE A1/A2/…/E, generic A–F). Pure function — called on every mark upsert so the stored `grade` can never drift from the percentage.

### [`ReportCardService`](../../backend/src/main/java/in/schoolapp/academics/ReportCardService.java)
Three-pass generation inside one transaction.
- `generateForSection(tenantId, examId, sectionId)` —
  1. **Aggregate** per student: total max, total obtained, percentage (2 dp, `HALF_UP`).
  2. **Dense rank**: ties share a rank, next rank continues sequentially (1, 2, 2, 4).
  3. **Render + persist + event** per student: `ReportCardPdfGenerator.generate` → upsert `ReportCard` on `(student, exam)` → `FileStorageService.store` at `report-cards/{tenantId}/{reportCardId}.pdf` → publish `ReportCardGeneratedEvent`.
- `getReportCard(tenantId, studentId, examId)` — throws `REPORT_CARD_NOT_READY` if the row does not exist.

`ReportCardDeliveryListener` (in `communication`) consumes the event AFTER_COMMIT + `@Async`, sends the WhatsApp with the PDF URL, and updates `wa_sent_at`.

### [`ReportCardPdfGenerator`](../../backend/src/main/java/in/schoolapp/academics/ReportCardPdfGenerator.java)
OpenPDF (LGPL) A4 layout — school header, student block, subject-wise marks table, totals + percentage + grade + rank, footer. Attendance section is passed `null` at the call site (not yet wired).

### [`TeacherAssignmentService`](../../backend/src/main/java/in/schoolapp/academics/TeacherAssignmentService.java)
CRUD over `teacher_subject_assignments`. The uniqueness rule prevents the same `(staff, subject, section, academic_year)` tuple from being assigned twice.
- `assign(tenantId, req)` — resolves the current academic year, dedupe-checks, persists, audit-logs.
- `listCurrent(tenantId)` / `listForStaff(tenantId, staffId)` — current-year slices.
- `unassign(tenantId, assignmentId)` — hard delete + audit.

### [`ExamEligibilityService`](../../backend/src/main/java/in/schoolapp/academics/ExamEligibilityService.java)
Per-section attendance-% eligibility (LLD §5.2 / gap §5.2).
- `forSection(tenantId, sectionId, windowDays)` — loads the active roster, reads `minAttendancePct` from `school.settings.minAttendancePct` (default 75, clamped to `[0, 100]`), computes per-student attendance pct over the last `windowDays` days (default 90) via `AttendanceRepository.countPerStudentInWindow`, and flags each student `eligible = pct >= minPct`. Sorted by roll number (nulls last), then by name.

---

## Endpoints

All under `/api/v1/tenants/{tenantId}`.

| Method | Path | `@PreAuthorize` |
|---|---|---|
| POST | `/subjects/bulk` | `OWNER_OR_ADMIN` |
| GET | `/subjects` | JWT |
| POST | `/exams` | `OWNER_OR_ADMIN` |
| GET | `/exams` | JWT |
| POST | `/exams/{examId}/publish` | `OWNER_OR_ADMIN` |
| GET | `/exams/{examId}/marks/{sectionId}` | JWT |
| POST | `/exams/{examId}/marks` | `MARKS_WRITER` |
| GET | `/exams/{examId}/completion/{sectionId}` | JWT |
| POST | `/exams/{examId}/report-cards/generate/{sectionId}` | `OWNER_OR_ADMIN` |
| GET | `/students/{studentId}/report-card/{examId}` | JWT |
| GET | `/sections/{sectionId}/exam-eligibility?windowDays=` | JWT |
| POST | `/teacher-assignments` | `OWNER_OR_ADMIN` |
| GET | `/teacher-assignments?staffId=` | JWT |
| DELETE | `/teacher-assignments/{id}` | `OWNER_OR_ADMIN` |

`MARKS_WRITER` = `SCHOOL_OWNER`, `PRINCIPAL`, `ADMIN`, `CLASS_TEACHER`, `SUBJECT_TEACHER`.

---

## Key design decisions

- **Grid-style marks entry.** Teachers submit the whole section × one subject at a time; backend upserts per `(exam, student, subject)` so re-submitting the sheet is safe and corrections are a no-fuss re-save.
- **Grade is auto-derived on every write**, board-aware (`School.board`). Stored `grade` cannot drift from percentage — no "refresh grades" maintenance job exists because none is needed.
- **Publish is a one-way lock.** Once `Exam.isPublished=true`, `MarksService.submitBulk` refuses writes with `MARKS_ALREADY_FINALIZED`. The migration path (`createHistorical`) deliberately bypasses this — historical paper marks predate the publish concept. Publishing is audit-logged.
- **Cross-tenant subject injection is blocked at the service boundary.** Every submitted `subjectId` must belong to the caller's tenant; a JWT bound to tenant A cannot sneak tenant B's subject in.
- **Report-card generation is idempotent.** The `UNIQUE(student_id, exam_id)` constraint makes regeneration an upsert — fix a mark, regenerate, the same row gets a fresh PDF + a fresh event.
- **Dense ranking, not ordinal.** Ties share a rank and the next rank continues sequentially (1, 2, 2, 4) — matches what Indian school report cards show.
- **Field is named `submitFinal`, not `finalize`** — Java records can't have a component named `finalize` (clashes with `Object.finalize()`).
- **Exam eligibility reads `minAttendancePct` from school settings**, not a hard-coded constant — onboarding seeds 75, admins can raise/lower per policy.
- **`windowDays` on the eligibility endpoint is optional**; default 90, and non-positive values fall back to the default.
- **Teacher-subject assignments are scoped to an academic year** — the same teacher teaching the same section next year is a separate row, preserving history.

---

## Relationships to other modules

- **Reads from `school`:** `School.board` (grade scale) + `school.settings.minAttendancePct`, `AcademicYearService.getCurrentOrThrow`, `ClassSectionService.getSectionOrThrow`.
- **Reads from `student`:** `StudentService.getStudentEntity`, `StudentEnrollmentRepository.findBySectionIdAndStatus(ACTIVE)` (roster), `StudentRepository.findAllById` (name + admission number enrichment).
- **Reads from `attendance`:** `AttendanceRepository.countPerStudentInWindow` (exam eligibility + future report-card attendance block).
- **Reads from `storage`:** `FileStorageService.store` for report-card PDFs.
- **Reads from `audit`:** `AuditLogger` (exam publish, teacher-assignment create/delete).
- **Written to by:** `migration` via `MarksService.createHistorical`; `student.StudentTimelineService` reads `ReportCardRepository` + `ExamRepository` for the cross-year profile.
- **Events published:** [`ReportCardGeneratedEvent`](../../backend/src/main/java/in/schoolapp/academics/event/ReportCardGeneratedEvent.java) — consumed by `ReportCardDeliveryListener` (communication).
- **Events consumed:** none.

---

## Related migrations

- **V1** — creates `subjects` (`UNIQUE(school_id, name)`), `teacher_subject_assignments` (`UNIQUE(staff_id, subject_id, section_id, academic_year_id)` + `idx_tsa_staff_year`), `exams` (+ `idx_exams_year`), `exam_marks` (`UNIQUE(exam_id, student_id, subject_id)` + `idx_exam_marks_student`, `idx_exam_marks_section`), `report_cards` (`UNIQUE(student_id, exam_id)` with delivery-telemetry columns `wa_sent_at` / `wa_delivered_at` / `wa_read_at`). Roster reads rely on `student_enrollments.idx_enrollments_section`.
