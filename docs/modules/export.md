# `export_` module

Streaming CSV / XLSX exports for students, fee payments, and attendance. OOM-safe on large datasets; every export is audit-logged.

**Package:** `in.schoolapp.export_` (trailing underscore because `export` is a reserved Java keyword)

---

## Purpose

Data-portability endpoints for DPDP Act 2023 / gap analysis §8: an owner or admin clicks "export" on the dashboard and the response body starts streaming immediately. The file never materialises in server memory — pages of 500 rows are pulled from the DB and handed to the format writer, which flushes directly to the servlet `OutputStream`. XLSX uses POI's `SXSSFWorkbook` so only a rolling window of rows is kept in heap while older rows spill to compressed temp files. A 10k-student school exports without OOM. Every export also writes an `audit_log` row, because DPDP expects a signed trail of who pulled the dataset and when.

---

## Entities / tables

None. The module reads from `students`, `fee_payments`, and `attendance_records` and writes only to `audit_log` via [`AuditLogger`](../../backend/src/main/java/in/schoolapp/audit/AuditLogger.java).

---

## Types

### [`ExportFormat`](../../backend/src/main/java/in/schoolapp/export_/ExportFormat.java)

Enum: `CSV` · `XLSX`. CSV for importing into other systems and diffing; XLSX for principals who open the file in Excel. Default is `XLSX` when the client doesn't specify.

---

## Services + key public methods

### [`ExportService`](../../backend/src/main/java/in/schoolapp/export_/ExportService.java)

All three export methods are `@Transactional(readOnly = true)` and stream straight to an `OutputStream`.

| Method | Signature | Notes |
|---|---|---|
| `exportStudents` | `(tenantId, format, out)` | Active students only. Paginates `StudentRepository.findBySchoolIdAndActiveTrue`. |
| `exportFeePayments` | `(tenantId, format, out)` | All payments, newest first. Paginates `FeePaymentRepository.findBySchoolIdOrderByPaymentDateDesc`. |
| `exportAttendance` | `(tenantId, from, to, format, out)` | Range-filtered. Paginates `AttendanceRepository.findBySchoolIdAndDateBetweenOrderByDateAscStudentIdAsc`. |

Constants:

- `PAGE_SIZE = 500` — DB chunk size. Small enough to fit comfortably in heap; large enough that a 10k-row export is ~20 round-trips.
- `XLSX_ROWS_IN_MEMORY = 100` — SXSSF rolling window. POI's recommended default; raising it increases memory with no correctness benefit.

### Format writers

- **CSV** — `BufferedWriter` over UTF-8, `\r\n` line terminator, RFC 4180-ish quoting: wraps a cell in double quotes when it contains a comma, double quote, `\n`, or `\r`; embedded quotes are doubled (`"` → `""`).
- **XLSX** — `SXSSFWorkbook(100)` with `setCompressTempFiles(true)` so the rolling-window spill files stay small. Rows beyond the 100-row window flush to compressed temp files; `try-with-resources` cleans them on close.

### Audit

Every successful export calls `auditLogger.logAction(tenantId, "<EntityType>", UUID.randomUUID(), "DATA_EXPORT", details)`. Students / fees pass an empty detail map; attendance passes `{from, to}`. A random UUID is used for `entityId` because an export event doesn't correspond to a single row — it's an action over a range.

---

## Endpoints

All three stream the body directly. `Content-Disposition: attachment` + a synthesised filename; `X-Accel-Buffering: no` hints reverse proxies not to buffer so clients see progress on multi-minute exports.

| Path | Method | `@PreAuthorize` | Description |
|---|---|---|---|
| `/api/v1/tenants/{tenantId}/export/students?format=CSV\|XLSX` | GET | `AppRoles.OWNER_OR_ADMIN` | Active students. |
| `/api/v1/tenants/{tenantId}/export/fees?format=CSV\|XLSX` | GET | `AppRoles.FEE_WRITER` | All fee payments. |
| `/api/v1/tenants/{tenantId}/export/attendance?from=&to=&format=CSV\|XLSX` | GET | `AppRoles.OWNER_OR_ADMIN` | Attendance between `from` and `to` inclusive; rejects inverted ranges with `VALIDATION_ERROR`. |

Controller: [`ExportController`](../../backend/src/main/java/in/schoolapp/export_/ExportController.java).

Content types:

- CSV: `text/csv; charset=UTF-8`
- XLSX: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`

---

## Design decisions

- **Stream or die.** No `List<Row>` ever holds the full dataset. Controller opens the servlet `OutputStream`, service paginates and writes. On a 10k-row school this is the difference between a 2 GB heap spike and a 50 MB one.
- **SXSSF, not XSSF.** POI's standard `XSSFWorkbook` keeps every row in memory. `SXSSFWorkbook` with a 100-row window + `setCompressTempFiles(true)` lets large workbooks spill to disk transparently — the spill files are cleaned on close via try-with-resources.
- **Role split between exports.** Fees need `FEE_WRITER` (accountants included) because the accountant's core job benefits from a full payments export; students + attendance stay at `OWNER_OR_ADMIN` because those carry more PII and a chronic-teacher role shouldn't be able to walk away with the full student list.
- **Audit is an `ACTION`, not a row diff.** Exports don't map to a single entity id, so we use `UUID.randomUUID()` for the audit row's `entity_id` and route through `logAction("DATA_EXPORT", ...)`. The range params are in `details` so a compliance review can reconstruct what was pulled.
- **`X-Accel-Buffering: no`.** Reverse proxies (nginx/K8s ingress) would otherwise buffer the whole response before forwarding, defeating the streaming design. Clients would see "nothing for 90 seconds, then a file."
- **Inverted-range check is a 400, not a silent empty file.** `exportAttendance` validates `from <= to` with an explicit `AppException(VALIDATION_ERROR)`. An export that silently returns an empty sheet is harder to diagnose than an explicit error.
- **Package name `export_`.** `export` is a reserved keyword in newer Java releases (module-info) and the trailing underscore is the least-invasive workaround. Imports and the `@RequestMapping` path are unaffected.

---

## Cross-module calls

### Depends on

- [`student`](student.md) — `StudentRepository`.
- [`fee`](fee.md) — `FeePaymentRepository`.
- [`attendance`](attendance.md) — `AttendanceRepository`.
- [`audit`](audit.md) — `AuditLogger.logAction`.
- [`auth`](auth.md) — `AppRoles` constants.
- [`common`](common.md) — `AppException` / `ErrorCode` for the inverted-range rejection.
- Apache POI `poi-ooxml` 5.3.0 — `SXSSFWorkbook`, `Sheet`, `Row`.

### Depended on by

No backend consumers. Export is a leaf — only the frontend calls it.

---

## Relevant migrations

None. All tables read are created in [V1](../../backend/src/main/resources/db/migration/V1__initial_schema.sql); the `audit_log` row written after each export is also a V1 table. No schema changes for the export surface itself.
