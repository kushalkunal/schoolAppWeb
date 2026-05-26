# `student` module

Students, parents, sibling detection, family view, typed document uploads, and a cross-year timeline.

**Package:** `in.schoolapp.student`

---

## Entities

| Entity | Table | Notes |
|---|---|---|
| [`Student`](../../backend/src/main/java/in/schoolapp/student/entity/Student.java) | `students` | Core record. Admission number is optional + unique per tenant when present (partial unique index). |
| [`Parent`](../../backend/src/main/java/in/schoolapp/student/entity/Parent.java) | `parents` | Phone is unique per tenant — this is the sibling-detection pivot. |
| [`StudentParentLink`](../../backend/src/main/java/in/schoolapp/student/entity/StudentParentLink.java) | `student_parent_links` | Many-to-many join. `is_primary=true` marks the contact used for notifications (exactly one primary per student, enforced in service code). |
| [`StudentEnrollment`](../../backend/src/main/java/in/schoolapp/student/entity/StudentEnrollment.java) | `student_enrollments` | One row per `(student, academic_year)` — the timeline source. |
| [`StudentDocument`](../../backend/src/main/java/in/schoolapp/student/entity/StudentDocument.java) | `student_documents` | Typed uploaded document metadata (V5). Actual file lives in `FileStorageService`. |
| [`StudentDocumentType`](../../backend/src/main/java/in/schoolapp/student/entity/StudentDocumentType.java) | enum | `ADMISSION_FORM`, `BIRTH_CERT`, `TC`, `PHOTO`, `MEDICAL`, `OTHER` |
| [`ParentRelation`](../../backend/src/main/java/in/schoolapp/student/entity/ParentRelation.java) | enum | `FATHER`, `MOTHER`, `GUARDIAN` |
| [`EnrollmentStatus`](../../backend/src/main/java/in/schoolapp/student/entity/EnrollmentStatus.java) | enum | `ACTIVE`, `LEFT`, `GRADUATED` |

---

## Services

### [`StudentService`](../../backend/src/main/java/in/schoolapp/student/StudentService.java)
Student lifecycle + uploads.
- `createStudent(tenantId, req)` — the 3-field create (LLD §5.1). Creates a `Student` + `StudentParentLink` + `StudentEnrollment` against the current academic year; the parent is resolved via `ParentService.findOrCreate` so an existing phone makes the new student a sibling of the existing parent's other children.
- `createHistoricalStudent(...)` — called by the OCR migration commit flow; tolerates more missing fields because handwritten forms often only have a name + class.
- `listStudents(tenantId, search, page, size)` — paginated list; when `search` is non-blank, delegates to `StudentRepository.searchBySchoolId` (trigram fuzzy match via `idx_students_name_trgm`).
- `getStudentEntity(tenantId, studentId)` — tenant-scoped fetch used across modules.
- `deactivateStudent(...)` — soft-delete (`is_active=false`).
- `updateStudent(tenantId, studentId, req)` — patch-style `PUT`. Only non-null fields of `UpdateStudentRequest` are applied (`firstName`, `lastName`, `gender`, `dateOfBirth`, `bloodGroup`, `address`). Does not touch enrollment or parent links.
- `uploadPhoto(tenantId, studentId, bytes, contentType)` — stores at the deterministic key `students/{tenantId}/{studentId}/photo{ext}` (overwrite-friendly), updates `photo_url`.
- `uploadDocument(tenantId, studentId, type, bytes, contentType, originalFilename)` — new file per upload at `student-docs/{tenantId}/{studentId}/{docType}/{uuid}-{sanitisedName}`; writes a `student_documents` row carrying storage key, URL, size, content type, uploader. Filename is sanitised to strip path separators / unsafe characters.
- `listDocuments(tenantId, studentId)` / `deleteDocument(tenantId, documentId)` — documents listed newest-first; delete removes the file from storage **and** the metadata row.

### [`ParentService`](../../backend/src/main/java/in/schoolapp/student/ParentService.java)
- `findOrCreate(schoolId, rawPhone, name, email)` — the sibling-detection primitive. Phone is normalised first, then `findBySchoolIdAndPhone` returns the existing parent or a freshly-persisted one. Existing records are never clobbered on re-link — name/email only land on creation.

### [`FamilyService`](../../backend/src/main/java/in/schoolapp/student/FamilyService.java)
Assembles the family view across siblings, primary parent, and joined class/section names.
- `getPrimaryParentId(studentId)` / `getPrimaryParent(studentId)` — for notification routing.
- `findSiblings(studentId)` — all students sharing any parent with the given student.
- `getProfile(tenantId, studentId)` — current enrollment + all parents (with `isPrimary` flag) + siblings, joined to class/section names.
- `getFamilyByParent(tenantId, parentId)` — the "my children" view for a parent-scoped UI.
- `groupStudentsByPrimaryParent(students)` — pivot used by `AbsenceAlertService` and `ReceiptDeliveryListener` to send one combined WhatsApp per family instead of N per parent. Students without a primary parent are omitted — the caller decides what to do with those.

### [`StudentTimelineService`](../../backend/src/main/java/in/schoolapp/student/StudentTimelineService.java)
Read-only cross-year profile aggregate (no new tables — joins existing data).
- `get(tenantId, studentId)` — returns `enrollments` (newest-first, joined to academic year + class + section names), `reportCards` (via `ReportCardRepository`, joined to exam name), and `attendance` (per-year rollup computed from `AttendanceRepository.countPerStudentInWindow` over each academic year's `(start_date, end_date)` window).

---

## Endpoints

All routes under `/api/v1/tenants/{tenantId}/students`. `TenantInterceptor` enforces the tenant-id / JWT-claim match.

| Method | Path | `@PreAuthorize` |
|---|---|---|
| POST | `/students` | `OWNER_OR_ADMIN` |
| GET | `/students?search=&page=&size=` | JWT |
| GET | `/students/{studentId}` | JWT (returns full `StudentProfileResponse` — enrollment + parents + siblings) |
| PUT | `/students/{studentId}` | `OWNER_OR_ADMIN` |
| DELETE | `/students/{studentId}` | `OWNER_OR_ADMIN` |
| GET | `/students/{studentId}/timeline` | JWT |
| POST | `/students/{studentId}/photo` (multipart) | `OWNER_OR_ADMIN` |
| POST | `/students/{studentId}/documents?type=` (multipart) | `OWNER_OR_ADMIN` |
| GET | `/students/{studentId}/documents` | JWT |
| DELETE | `/students/documents/{documentId}` | `OWNER_OR_ADMIN` |

---

## Key design decisions

- **3-field create** (`firstName`, `sectionId`, `parentPhone`) — everything else is optional. No mandatory-field wall blocks setup.
- **Parent phone is the sibling-detection pivot.** Same `(tenantId, phone)` → same `Parent` row → the new student joins the family. Downstream sibling-aware notifications (`FamilyService.groupStudentsByPrimaryParent`) collapse two children into one WhatsApp per family.
- **Trigram fuzzy search** on `(first_name || ' ' || last_name)` via V1's `idx_students_name_trgm` — used by the picker UI and by OCR entity matching in the migration module.
- **One enrollment per `(student, academic_year)`** (unique constraint). The list ordered by year is the student's timeline — no separate "history" table needed.
- **Documents land in the storage backend with a generated UUID filename prefix**, not the raw uploaded name, so filename collisions and path-traversal attempts are structurally impossible. The original filename (sanitised) is kept in the metadata row for display.
- **Document delete is hard — both blob and row**, because the metadata row has no standalone meaning once the blob is gone.
- **Photo uploads overwrite the same storage key**; document uploads create new keys (they're a history, photos are a current-state attribute).
- **Timeline is a read-only aggregate**, not a materialised table — cheap enough to compute on demand for the profile screen.

---

## Relationships to other modules

- **Reads from `school`:** `Section`, `AcademicYear`, `SchoolClass` via their services (`ClassSectionService.getSectionOrThrow`, `AcademicYearService.getCurrentOrThrow`).
- **Reads from `attendance`:** `AttendanceRepository.countPerStudentInWindow` (timeline yearly rollup).
- **Reads from `academics`:** `ReportCardRepository`, `ExamRepository` (timeline report cards block).
- **Reads from `storage`:** `FileStorageService` (photo + document store/delete).
- **Reads from `audit`:** `AuditLogger`.
- **Written to by:**
  - `migration` — `createHistoricalStudent` when committing an OCR-migrated paper form.
  - `fee`, `attendance`, `academics`, `communication` — all read students through this module to resolve names / siblings / primary parent.
- **Events:** none published, none consumed.

---

## Related migrations

- **V1** — creates `students`, `parents` (`UNIQUE(school_id, phone)`), `student_parent_links`, `student_enrollments` (`UNIQUE(student_id, academic_year_id)`), plus `idx_students_name_trgm` (pg_trgm), `idx_students_school_active`, `idx_student_parent_primary` (partial on `is_primary=true`), `idx_enrollments_section`, and the partial unique `uq_students_admission_number` on `admission_number` where present.
- **V5** — creates `student_documents` with `doc_type`, `storage_key`, `file_url`, `file_name`, `content_type`, `size_bytes`, `uploaded_by_id`; indexes on `(student_id, created_at DESC)` and `(school_id, created_at DESC)`.
