# `migration` module

Paper-register ingestion. Photo → OCR → LLM → human review → commit as historical records.

**Package:** `in.schoolapp.migration`

---

## Why this module exists

Small schools have years of paper data — fee receipt books, attendance registers, handwritten
mark sheets, admission forms. Every existing SMS product answers "digitise first, then upload our
CSV"; that's 80+ hours of manual typing. This module reduces cold-start effort from weeks to an
afternoon per paper book.

---

## Pipeline

```
  Client                         Migration module                        Providers
    │                                    │                                    │
    │  POST /migration                   │                                    │
    │  (multipart: file + type)          │                                    │
    ├───────────────────────────────────▶│                                    │
    │                                    │  fileStorage.store(…)               │
    │                                    │  create MigrationJob (UPLOADED)     │
    │                                    │  publishEvent(MigrationJobUploaded) │
    │  201 {jobId, status=UPLOADED}      │                                    │
    │◀───────────────────────────────────┤                                    │
    │                                    │                                    │
    │                            ┌───────┴────────┐                           │
    │                            │  @Async        │                           │
    │                            │  MigrationProc │                           │
    │                            │  on ocrExecutor│                           │
    │                            └───────┬────────┘                           │
    │                                    │   status=PROCESSING                │
    │                                    │  OcrProvider.extract(bytes)         │
    │                                    ├───────────────────────────────────▶│ Google Vision OCR
    │                                    │  LlmExtractionProvider.extract(     │
    │                                    │       text, type)                   │
    │                                    ├───────────────────────────────────▶│ OpenAI / Anthropic /
    │                                    │                                    │ Gemini
    │                                    │   status=REVIEW                    │
    │                                    │   or FAILED + error_message        │
    │                                    │                                    │
    │  GET /migration/{jobId} (poll)     │                                    │
    ├───────────────────────────────────▶│                                    │
    │                                    │  buildReviewableRecords:            │
    │                                    │  - entityMatching.match(name)       │
    │                                    │  - confidence score per row         │
    │  200 {status=REVIEW, reviewableRecords[]}                               │
    │◀───────────────────────────────────┤                                    │
    │                                    │                                    │
    │  POST /migration/{jobId}/commit    │                                    │
    │  (ConfirmedRow[])                  │                                    │
    ├───────────────────────────────────▶│                                    │
    │                                    │  per row: dispatch by jobType      │
    │                                    │   → createHistorical*               │
    │                                    │  status=COMMITTED                   │
    │  200 {status=COMMITTED, matchedCount}
    │◀───────────────────────────────────┤
```

---

## Job lifecycle ([`MigrationJobStatus`](../../backend/src/main/java/in/schoolapp/migration/entity/MigrationJobStatus.java))

```
  UPLOADED → PROCESSING → REVIEW → COMMITTED
  any state → FAILED  (terminal, error_message populated)
```

`UPLOADED` lasts only until the async listener picks up the job; `PROCESSING` typically 5–60 s
depending on provider; `REVIEW` until a human calls the commit endpoint; `COMMITTED` is terminal.
`MigrationProcessor` catches all exceptions and marks the job `FAILED` rather than letting them
escape — the upload HTTP response has already returned, so throwing would only log.

---

## Layout

```
in.schoolapp.migration/
├── MigrationJobService                  Lifecycle + per-type commit dispatch
├── MigrationProcessor                   @Async OCR → LLM → match pipeline (on ocrExecutor)
├── EntityMatchingService                Trigram pre-filter + Levenshtein fuzzy name match
├── MigrationController                  /api/v1/tenants/{id}/migration
├── entity/
│   ├── MigrationJob                     One paper artefact being migrated
│   ├── MigrationJobType                 FEE_RECEIPT | ATTENDANCE | MARKS | ADMISSION_FORM
│   └── MigrationJobStatus               UPLOADED | PROCESSING | REVIEW | COMMITTED | FAILED
├── repository/
│   └── MigrationJobRepository
├── event/
│   └── MigrationJobUploadedEvent
├── ocr/
│   ├── OcrProvider                      interface
│   ├── LoggingOcrProvider               @ConditionalOnProperty: LOGGING (default, matchIfMissing)
│   ├── GoogleCloudVisionOcrProvider     @ConditionalOnProperty: GOOGLE_CLOUD_VISION
│   ├── config/OcrProperties
│   └── dto/OcrResult
├── llm/
│   ├── LlmExtractionProvider            interface
│   ├── LoggingLlmProvider               @ConditionalOnProperty: LOGGING (default, matchIfMissing)
│   ├── OpenAiLlmProvider                @ConditionalOnProperty: OPENAI
│   ├── AnthropicLlmProvider             @ConditionalOnProperty: ANTHROPIC
│   ├── GeminiLlmProvider                @ConditionalOnProperty: GEMINI
│   ├── PromptTemplates                  Per-type user prompts (shared across providers)
│   ├── LlmJsonParser                    Tolerant JSON parser (strips markdown fences)
│   ├── config/LlmProperties
│   └── dto/ExtractedRecord              LLM output shape
└── dto/
    ├── MigrationJobResponse
    ├── ReviewableRecord                 Extracted record + match candidates + confidence
    ├── MatchCandidate
    ├── MatchResult
    └── CommitMigrationRequest           Human-confirmed rows (flat record w/ per-type fields)
```

---

## Pluggable providers

Both concerns switch independently via `@ConditionalOnProperty`. Exactly one OCR + one LLM
implementation loads per JVM.

```yaml
app:
  migration:
    ocr:
      provider: LOGGING              # LOGGING | GOOGLE_CLOUD_VISION
      google-cloud-vision:
        api-key: AIza...
        feature-type: DOCUMENT_TEXT_DETECTION
    llm:
      provider: LOGGING              # LOGGING | OPENAI | ANTHROPIC | GEMINI
      openai:
        api-key: sk-...
        model: gpt-4o-mini
      anthropic:
        api-key: sk-ant-...
        model: claude-haiku-4-5-20251001
      gemini:
        api-key: AIza...
        model: gemini-1.5-flash
```

Mix and match — `OCR=GOOGLE_CLOUD_VISION` + `LLM=LOGGING` to test real OCR cheaply, `OCR=LOGGING` +
`LLM=GEMINI` to test real LLM cheaply, both `LOGGING` for end-to-end dev with zero credentials.

---

## Commit dispatch

[`MigrationJobService.commitRow`](../../backend/src/main/java/in/schoolapp/migration/MigrationJobService.java)
switch-dispatches by `MigrationJobType`:

| Type | Calls | Fields required on `ConfirmedRow` |
|---|---|---|
| `FEE_RECEIPT` | [`FeePaymentService.createHistorical`](../../backend/src/main/java/in/schoolapp/fee/FeePaymentService.java) | `studentId`, `amountPaise`, `paymentMode` (+ optional `paymentDate`, `externalReceiptNumber`, `notes`) |
| `ATTENDANCE` | [`AttendanceService.createHistorical`](../../backend/src/main/java/in/schoolapp/attendance/AttendanceService.java) | `studentId`, `sectionId`, `attendanceDate`, `attendanceStatus` |
| `MARKS` | [`MarksService.createHistorical`](../../backend/src/main/java/in/schoolapp/academics/MarksService.java) (one call per subject mark) | `studentId`, `examId`, `subjectId`, `sectionId`, `maxMarks` (+ `obtainedMarks` or `absent=true`) |
| `ADMISSION_FORM` | [`StudentService.createHistoricalStudent`](../../backend/src/main/java/in/schoolapp/student/StudentService.java) | `firstName`, `sectionId`, `parentPhone` (+ optional `lastName`, `parentName`, `dateOfBirth`, `gender`) |

`requireField()` enforces per-type presence before delegating. `createHistorical*` methods skip
downstream listeners — no WhatsApp receipts go out for old payments, no absence alerts for old
attendance rows. The original paper artefact already reached the parent.

After a successful commit the full `req.rows()` payload is serialised onto `MigrationJob.reviewedJson`
for audit, `matchedCount` is populated, and `completedAt` stamped.

---

## Entity matching ([`EntityMatchingService`](../../backend/src/main/java/in/schoolapp/migration/EntityMatchingService.java))

Given an extracted student name (often misspelled, abbreviated, or partial) → find the real row:

1. **Trigram pre-filter** — `students.idx_students_name_trgm` narrows to top 5 candidates via `ILIKE '%query%'`.
2. **Levenshtein scoring** — normalised distance → similarity in `[0, 1]`.
3. **Ranking** — sorted by confidence descending.
4. **Unambiguity threshold** — top ≥ 0.85 AND ≥ 0.10 gap to runner-up → flagged as auto-matched; otherwise all candidates returned for UI disambiguation.

Confidence bands surfaced to the reviewer:

- **HIGH** (≥ 0.85 unambiguous) — auto-approvable, included in "Confirm All Clean"
- **MEDIUM** (0.5–0.85 or close runner-up) — requires dropdown selection
- **LOW** (< 0.5 or no candidates) — requires manual student search

---

## Prompts ([`PromptTemplates`](../../backend/src/main/java/in/schoolapp/migration/llm/PromptTemplates.java))

One template per `MigrationJobType`, shared across LLM providers. All demand strict
`{"records": [...]}` JSON output. Provider-specific JSON enforcement:

- OpenAI — `response_format: {"type": "json_object"}`
- Anthropic — system-prompt instruction (Claude honours it reliably)
- Gemini — `generationConfig: {responseMimeType: "application/json"}`

[`LlmJsonParser`](../../backend/src/main/java/in/schoolapp/migration/llm/LlmJsonParser.java) is
tolerant — strips stray markdown fences some providers emit, returns an empty list on malformed
JSON rather than throwing. One bad extraction shouldn't crash the whole pipeline.

---

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/tenants/{tenantId}/migration` | Multipart upload — form fields `type` + `file` |
| GET | `/api/v1/tenants/{tenantId}/migration` | List all jobs for tenant |
| GET | `/api/v1/tenants/{tenantId}/migration/{jobId}` | Fetch one; includes `reviewableRecords[]` when `status=REVIEW` |
| POST | `/api/v1/tenants/{tenantId}/migration/{jobId}/commit` | Commit human-confirmed rows |

Multipart limit set in [`application.yml`](../../backend/src/main/resources/application.yml):
`spring.servlet.multipart.max-file-size: 10MB` (override via `MAX_UPLOAD_SIZE`).

---

## Dependencies

- **Reads from `common`:** `TenantContext`, `AppException`, `ErrorCode`
- **Reads from `student`:** trigram `searchBySchoolId` for entity matching
- **Calls at commit time:** `FeePaymentService.createHistorical`, `AttendanceService.createHistorical`, `MarksService.createHistorical`, `StudentService.createHistoricalStudent`
- **Uses `storage`:** `FileStorageService.store` for uploaded image persistence
- **Publishes:** `MigrationJobUploadedEvent`
- **Consumes events:** same — `MigrationProcessor.@EventListener` listens, runs on `ocrExecutor`
