# `exam_result` module

Component-wise exam structure, subject marks entry, automatic result computation, ranked results,
PDF report cards, and publish-with-notification — the full MVP exam lifecycle for a real school.

**Package:** `in.schoolapp.academics` (extends the existing academics package)

---

## 1. Architecture Overview

```
Admin configures exam structure
        ↓
   ExamSubjectConfig
   (Theory 70 / Practical 30)
        ↓
Teacher enters component marks
        ↓
   ExamComponentMark
   (per student, per component)
        ↓
System rolls up to ExamMark
   (total obtained / max per subject)
        ↓
System computes ExamResult
   (total, %, grade, rank, pass/fail)
        ↓
Admin publishes → ReportCard PDF
        ↓
WhatsApp + Email notification to parents
```

### Multi-tenant boundary
Every table carries `school_id` (non-nullable, non-updatable) and is queried with an explicit
`school_id` predicate — identical to every other module.

### Result status workflow

```
DRAFT ──► READY ──► PUBLISHED
  │          │
  │   (all component marks entered)
  │          │
  └──────────┘ (admin can reopen/re-compute)
```

| Status | Meaning |
|--------|---------|
| `DRAFT` | Marks incomplete — some subjects still missing |
| `READY` | All component marks entered; result auto-computed |
| `PUBLISHED` | Admin published; PDF sent; parents notified |

---

## 2. Database Design

### New tables (V28 migration)

#### `exam_subject_configs` — marking scheme blueprint

```sql
CREATE TABLE exam_subject_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    exam_id         UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    subject_id      UUID NOT NULL REFERENCES subjects(id),
    component_name  VARCHAR(50) NOT NULL,   -- "Theory", "Practical", "Internal", "Viva"
    max_marks       NUMERIC(6,2) NOT NULL,
    passing_marks   NUMERIC(6,2),
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(exam_id, subject_id, component_name)
);
```

One row per component per subject per exam. A subject can have multiple rows
(e.g., Science → Theory 70, Practical 30).

#### `exam_component_marks` — teacher marks entry

```sql
CREATE TABLE exam_component_marks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES schools(id),
    config_id   UUID NOT NULL REFERENCES exam_subject_configs(id) ON DELETE CASCADE,
    student_id  UUID NOT NULL REFERENCES students(id),
    section_id  UUID NOT NULL REFERENCES sections(id),
    obtained    NUMERIC(6,2),       -- NULL = not yet entered
    is_absent   BOOLEAN NOT NULL DEFAULT FALSE,
    is_draft    BOOLEAN NOT NULL DEFAULT TRUE,
    remarks     TEXT,               -- teacher can annotate per-component
    entered_by  UUID REFERENCES staff(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(config_id, student_id)
);
```

#### `exam_results` — computed result per student per exam

```sql
CREATE TABLE exam_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id),
    exam_id         UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES students(id),
    section_id      UUID NOT NULL REFERENCES sections(id),
    total_max       NUMERIC(7,2) NOT NULL,
    total_obtained  NUMERIC(7,2) NOT NULL,
    percentage      NUMERIC(5,2) NOT NULL,
    grade           VARCHAR(5),
    rank_in_section INTEGER,
    is_pass         BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(15) NOT NULL DEFAULT 'DRAFT',   -- DRAFT / READY / PUBLISHED
    computed_at     TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(exam_id, student_id)
);
```

### Additions to existing tables

```sql
-- exams: class/section scoping + result lifecycle status
ALTER TABLE exams
    ADD COLUMN class_id         UUID REFERENCES school_classes(id),
    ADD COLUMN section_id       UUID REFERENCES sections(id),
    ADD COLUMN result_status    VARCHAR(15) NOT NULL DEFAULT 'DRAFT';

-- exam_marks: teacher remarks per subject
ALTER TABLE exam_marks
    ADD COLUMN remarks TEXT;
```

### Indexes

```sql
CREATE INDEX idx_esc_exam_subject  ON exam_subject_configs(exam_id, subject_id);
CREATE INDEX idx_ecm_config_section ON exam_component_marks(config_id, section_id);
CREATE INDEX idx_ecm_student        ON exam_component_marks(student_id, section_id);
CREATE INDEX idx_er_exam_section    ON exam_results(exam_id, section_id);
CREATE INDEX idx_er_exam_status     ON exam_results(exam_id, status);
```

---

## 3. Entities

| Entity | Table | Notes |
|--------|-------|-------|
| `ExamSubjectConfig` | `exam_subject_configs` | Component blueprint; unique per `(exam_id, subject_id, component_name)`. |
| `ExamComponentMark` | `exam_component_marks` | Teacher's marks entry per component per student; upsert-keyed on `(config_id, student_id)`. |
| `ExamResult` | `exam_results` | Auto-computed aggregate; unique per `(exam_id, student_id)`. Re-computation is a safe upsert. |
| `ResultStatus` | enum | `DRAFT`, `READY`, `PUBLISHED` |

---

## 4. Services

### `ExamStructureService`

Manages the per-exam subject-component configuration (the marking scheme).

- `configure(tenantId, examId, req)` — replaces the existing structure for an `(exam, subject)` tuple.
  Validates exam belongs to tenant. Old components for that subject are deleted and replaced atomically.
  Rejects configuration changes once the exam is published.
- `listStructure(tenantId, examId)` — returns all configs grouped by subject, ordered by `sort_order`.
- `getStructureMap(examId)` — package-private; returns `Map<UUID subjectId, List<ExamSubjectConfig>>`.

### `ComponentMarksService`

Teacher marks entry — component-aware replacement for the flat `MarksService.submitBulk`.

- `getEntrySheet(tenantId, examId, sectionId)` — returns roster × subject-components grid with any
  existing `obtained` values filled in. Groups subjects alphabetically; components sorted by `sort_order`.
- `submitBulk(tenantId, examId, req)` — upsert by `(config_id, student_id)`. Validates obtained ≤ max_marks.
  After each save, rolls up to `exam_marks` (subject total) so the existing `ReportCardService` remains
  compatible. If `submitFinal=true`, marks all entries `is_draft=false`.
- `getCompletionStatus(tenantId, examId, sectionId)` — per-subject completion count vs roster size.

### `ResultService`

Computes and persists `ExamResult` rows. Called automatically when `submitFinal=true`.

- `computeForSection(tenantId, examId, sectionId)` — for each student:
  1. Sum `exam_marks.obtained_marks` across all subjects.
  2. Sum `exam_marks.max_marks` for denominator.
  3. Compute percentage (2 dp, HALF_UP).
  4. Derive grade via `GradeCalculator`.
  5. Determine pass/fail: pass = all subjects have `obtained >= passing_marks`.
  6. Upsert `ExamResult` with `status=READY`.
  7. Dense-rank within section by percentage descending.
- `publishSection(tenantId, examId, sectionId)` — flips all `READY` results to `PUBLISHED`,
  sets `published_at`, triggers `ReportCardService.generateForSection`.
- `getResultsForSection(tenantId, examId, sectionId)` — returns list of `ExamResultResponse` ordered by rank.

### `ResultDashboardService`

Analytics read-side over `exam_results`.

- `getDashboard(tenantId, examId)` — returns:
  - overall pass % per section
  - section toppers (rank 1 per section)
  - subject average scores
  - grade distribution
  - failed students list
  - highest scorers

---

## 5. API Endpoints

All under `/api/v1/tenants/{tenantId}`. `TenantInterceptor` auto-enforces JWT tenant claim.

### Exam structure (marking scheme)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/exams/{examId}/structure` | `OWNER_OR_ADMIN` | Configure subject components |
| GET | `/exams/{examId}/structure` | JWT | Get marking scheme |

### Component marks entry

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/exams/{examId}/component-marks/{sectionId}` | JWT | Get entry sheet |
| POST | `/exams/{examId}/component-marks` | `MARKS_WRITER` | Submit bulk marks |
| GET | `/exams/{examId}/completion/{sectionId}` | JWT | Completion status |

### Results

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/exams/{examId}/results/compute/{sectionId}` | `OWNER_OR_ADMIN` | Trigger computation |
| GET | `/exams/{examId}/results/{sectionId}` | JWT | List results |
| POST | `/exams/{examId}/results/publish/{sectionId}` | `OWNER_OR_ADMIN` | Publish + notify |
| GET | `/exams/{examId}/dashboard` | JWT | Analytics dashboard |

### Report cards (existing, enhanced)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/exams/{examId}/report-cards/{sectionId}` | `OWNER_OR_ADMIN` | Generate PDFs |
| GET | `/exams/{examId}/report-cards/{sectionId}` | JWT | List report cards |
| GET | `/report-cards/{reportCardId}/pdf` | JWT | Download PDF |

---

## 6. Request / Response DTOs

### `ConfigureExamStructureRequest`

```json
{
  "subjectId": "uuid",
  "components": [
    { "componentName": "Theory", "maxMarks": 70, "passingMarks": 23, "sortOrder": 1 },
    { "componentName": "Practical", "maxMarks": 30, "passingMarks": 10, "sortOrder": 2 }
  ]
}
```

### `ExamStructureResponse`

```json
{
  "subjectId": "uuid",
  "subjectName": "Science",
  "components": [
    { "id": "uuid", "componentName": "Theory", "maxMarks": 70, "passingMarks": 23, "sortOrder": 1 },
    { "id": "uuid", "componentName": "Practical", "maxMarks": 30, "passingMarks": 10, "sortOrder": 2 }
  ],
  "totalMax": 100
}
```

### `ComponentMarksSheetResponse`

```json
{
  "students": [
    {
      "studentId": "uuid",
      "name": "Rahul Sharma",
      "rollNumber": 1,
      "subjects": [
        {
          "subjectId": "uuid",
          "subjectName": "Science",
          "components": [
            { "configId": "uuid", "componentName": "Theory", "maxMarks": 70, "obtained": 62, "absent": false },
            { "configId": "uuid", "componentName": "Practical", "maxMarks": 30, "obtained": 25, "absent": false }
          ]
        }
      ]
    }
  ]
}
```

### `ExamResultResponse`

```json
{
  "studentId": "uuid",
  "studentName": "Rahul Sharma",
  "rollNumber": 1,
  "totalMax": 500,
  "totalObtained": 412,
  "percentage": 82.40,
  "grade": "A2",
  "rankInSection": 3,
  "isPass": true,
  "status": "PUBLISHED"
}
```

### `ResultDashboardResponse`

```json
{
  "examId": "uuid",
  "examName": "Mid Term",
  "sections": [
    {
      "sectionId": "uuid",
      "sectionName": "10-A",
      "totalStudents": 40,
      "passCount": 35,
      "failCount": 5,
      "passPercentage": 87.5,
      "topper": { "studentId": "uuid", "name": "Priya", "percentage": 94.6 },
      "subjectAverages": [
        { "subjectName": "Math", "average": 72.3 }
      ],
      "gradeDistribution": { "A1": 5, "A2": 10, "B1": 12, "B2": 8, "C1": 3, "D": 2 }
    }
  ]
}
```

---

## 7. Frontend Pages

### Navigation

```
Exams
├── /academics/exams                 — Exam list (existing, enhanced)
├── /academics/exams/[id]/structure  — Configure marking scheme (admin)
├── /academics/exams/[id]/marks      — Component marks entry (teacher)
├── /academics/exams/[id]/results    — View results + dashboard (admin/teacher)
└── /academics/report-cards          — Browse/download report cards
```

### Screen designs

#### Exam List (`/academics/exams`)
- Table: Name | Type | Dates | Classes | Result Status | Actions
- Actions: Enter Marks | View Results | Configure Structure | Publish
- Status badge: DRAFT (gray) | READY (blue) | PUBLISHED (green)

#### Exam Structure (`/academics/exams/[id]/structure`)
- Subject dropdown to select which subject to configure
- Add components: name (free text) + max marks + passing marks
- Quick presets: "Theory + Practical (70+30)", "Theory only (100)", "Internal + Theory + Practical"
- Save per-subject; preview shows total marks per subject

#### Component Marks Entry (`/academics/exams/[id]/marks`)
- Section selector (breadcrumb: Class → Section)
- Sticky header row: Student | Subject1-Theory(70) | Subject1-Practical(30) | Subject2-Theory(100)…
- One row per student, one cell per component
- Cell validation: red border if > max marks, yellow if close to max
- Absent checkbox per component collapses marks to 0
- "Save Draft" (background) | "Submit Final" (prominent) buttons
- Completion indicator: "3/5 subjects complete"
- Auto-save on blur (debounced 800ms)

#### Results Dashboard (`/academics/exams/[id]/results`)
- Summary cards: Total Students | Pass | Fail | Pass %
- Topper card (name, %, grade)
- Result table: Rank | Name | Roll | Obtained/Total | % | Grade | Pass/Fail
- Subject-wise average bar chart
- Grade distribution pie/donut chart
- Export to CSV button
- "Publish Results" button (admin only) → triggers PDF + WhatsApp

#### Report Cards (`/academics/report-cards`)
- Filter: Exam | Class | Section
- Grid of student cards with: Name, Roll, %, Grade, Rank, PDF download button
- Bulk WhatsApp button (re-send)

---

## 8. PDF Report Card Layout

Generated by `ReportCardPdfGenerator` (OpenPDF, A4 portrait):

```
┌──────────────────────────────────────┐
│  [SCHOOL LOGO]  School Name          │
│  City | Board | Academic Year        │
├──────────────────────────────────────┤
│  REPORT CARD                         │
│  Student: Rahul Sharma  Roll: 12     │
│  Class: 10-A   Exam: Mid Term        │
├──────────────────────────────────────┤
│ Subject    Theory  Practical  Total  │
│ Math         85       -        85    │
│ Science      62      25        87    │
│ English      78       -        78    │
├──────────────────────────────────────┤
│ Total: 412 / 500  Percentage: 82.40% │
│ Grade: A2         Rank: 3 / 40       │
│ Result: PASS                         │
├──────────────────────────────────────┤
│ Attendance: 185/200 (92.5%)          │
│ Teacher Remarks: Good performance    │
├──────────────────────────────────────┤
│ Class Teacher _____  Principal _____ │
└──────────────────────────────────────┘
```

---

## 9. Notification Integration

On `publishSection`:
1. `ReportCardService.generateForSection` runs — persists PDFs.
2. `ApplicationEventPublisher` fires `ReportCardGeneratedEvent` per student.
3. `ReportCardDeliveryListener` (async, after-commit) reads primary parent from `FamilyService`.
4. Sends WhatsApp via `CommunicationService` with PDF URL + summary message.
5. Updates `report_cards.wa_sent_at`.

---

## 10. Role-Based Access

| Action | OWNER/ADMIN/PRINCIPAL | CLASS_TEACHER | SUBJECT_TEACHER |
|--------|----------------------|---------------|-----------------|
| Create/configure exam | ✓ | ✗ | ✗ |
| Configure marking scheme | ✓ | ✗ | ✗ |
| Enter marks | ✓ | ✓ (own sections) | ✓ (own subjects) |
| View results | ✓ | ✓ | ✓ |
| Publish results | ✓ | ✗ | ✗ |
| Download report cards | ✓ | ✓ | ✓ |

Backend enforcement via `@PreAuthorize` on controller methods.

---

## 11. Scalability & Future Directions

| Concern | MVP approach | Scale-out path |
|---------|-------------|----------------|
| Marks entry | Synchronous upsert | Async job queue for 1000+ students |
| PDF generation | On-demand in transaction | Async job + S3 storage |
| Ranking | In-memory dense-rank per section | Pre-computed via DB window function |
| Notifications | Async after-commit event | Outbox table for guaranteed delivery |
| Report card template | Hard-coded OpenPDF layout | Per-tenant Thymeleaf HTML → Chromium PDF |
| Grade scheme | CBSE/generic hard-coded | `GradeScheme` table per tenant |
| Analytics | Live aggregate queries | Nightly materialized views |
| Exam portals | None | Parent portal for self-serve PDF download |
