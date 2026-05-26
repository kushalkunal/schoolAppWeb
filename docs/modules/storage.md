# `storage` module

File-storage abstraction over local filesystem or any S3-compatible object store. Receipts, report cards, migration-job payloads, student photos + documents, and school logos all land here.

**Package:** `in.schoolapp.storage`

---

## Purpose

Binary blobs generated or uploaded by the app (fee receipt PDFs, report card PDFs, register-image uploads for migration OCR, student photos and documents, school logo) all persist through a single `FileStorageService` interface. The concrete backend is selected by `app.storage.provider` — `LOCAL` (default, filesystem + HTTP serving) for dev and single-node deploys, `S3` for any S3-compatible cloud (Cloudflare R2, Backblaze B2, MinIO self-hosted, AWS S3, DigitalOcean Spaces, Wasabi). Callers never see the difference: `store()` returns a `StoredFile` with a directly-usable URL that is either served by the local `FileController` or presigned (or public-base-URL) by the S3 client. Keys are slash-separated, flat enough to map onto both a directory tree and an S3 object key, and validated against path traversal on the LOCAL path.

No entities or tables. The consuming modules (`fee`, `academics`, `migration`, `student`, `school`) own their own persistence and stash only the `storage_key` + `file_url` the storage service returns.

---

## `FileStorageService` contract

[`FileStorageService`](../../backend/src/main/java/in/schoolapp/storage/FileStorageService.java) has four methods:

| Method | Behaviour |
|---|---|
| `StoredFile store(key, bytes, contentType)` | Persist the payload and return a `{key, url, sizeBytes, contentType}` handle. For LOCAL the URL is `publicBaseUrl/key`; for S3 it's a presigned URL (or public URL if `public-base-url` is set). |
| `byte[] retrieve(key)` | Read the raw bytes for a previously-stored key. Preferred over round-tripping via the URL when the caller is in-process (used by `MigrationProcessor` to re-read an uploaded register image without an HTTP hop). Throws `NoSuchElementException` on miss. |
| `void delete(key)` | Remove the file. No-op if already absent. S3 failures are logged and swallowed — orphaned objects are recoverable, and a failed delete isn't worth surfacing. |
| `String presignedUrl(key, ttl)` | Return a time-limited URL. LOCAL returns the stable `publicBaseUrl/key` (TTL ignored). S3 signs with the given TTL, capped at 7 days (S3 maximum). |

Keys look like:

- `receipts/{tenantId}/{paymentId}.pdf`
- `report-cards/{tenantId}/{reportCardId}.pdf`
- `migration/{tenantId}/{jobId}.bin`
- `students/{tenantId}/{studentId}/photo.jpg`
- `students/{tenantId}/{studentId}/docs/{uuid}.pdf`
- `schools/{tenantId}/logo.png`

---

## Services and beans

| File | When active | Notes |
|---|---|---|
| [`LocalFileStorageService`](../../backend/src/main/java/in/schoolapp/storage/LocalFileStorageService.java) | `app.storage.provider=LOCAL` (default) | Writes under `app.storage.local.base-dir`, resolves keys with `Path.resolve(...).normalize()` and asserts `startsWith(baseDir)` on every call to block traversal. Also rejects keys containing `..`, leading `/`, or backslashes up front. Creates parent dirs on write. |
| [`S3FileStorageService`](../../backend/src/main/java/in/schoolapp/storage/S3FileStorageService.java) | `app.storage.provider=S3` | MinIO Java SDK against any S3-compatible endpoint. Uses `putObject`, `getObject`, `removeObject`, `getPresignedObjectUrl`. Maps `NoSuchKey` → `NoSuchElementException` on retrieve; wraps all other failures in `AppException(EXTERNAL_SERVICE_ERROR)`. |
| [`FileController`](../../backend/src/main/java/in/schoolapp/storage/FileController.java) | `app.storage.provider=LOCAL` | Mounts `GET /files/{*key}` (unauthenticated — see Security below). Resolves the path via `LocalFileStorageService.resolve`, probes content type, serves `inline` with the original filename. |

Both providers are `@ConditionalOnProperty` — exactly one is active per boot, and switching is a single config change.

### Why MinIO SDK

- ~4× smaller on disk than AWS SDK v2 (~5 MB vs ~20 MB).
- Same S3 compatibility — works against R2, B2, MinIO, AWS S3, DO Spaces, Wasabi unchanged.
- Cleaner builder API.
- Apache 2.0 licensed.

---

## Configuration

```yaml
app:
  storage:
    provider: LOCAL                                # LOCAL | S3
    presign-ttl-minutes: 10080                     # 7 days, S3 max

    local:
      base-dir: ./storage
      public-base-url: http://localhost:8080/files

    s3:
      endpoint: https://<acct>.r2.cloudflarestorage.com
      region: auto                                 # "auto" for R2, "us-east-1" for AWS
      bucket: schoolapp-production
      access-key-id: ${S3_KEY}
      secret-access-key: ${S3_SECRET}
      public-base-url:                             # optional — public buckets skip presign
      force-path-style: false                      # true for MinIO self-hosted
```

Property binding: [`StorageProperties`](../../backend/src/main/java/in/schoolapp/storage/config/StorageProperties.java) with nested `LocalConfig` and `S3Config` records. `bucket` + `accessKeyId` + `secretAccessKey` are `@NotBlank` — missing values fail fast at startup with a clear message.

**Public-bucket shortcut.** If `s3.public-base-url` is set, `store()` returns `publicBaseUrl/key` directly and skips presigning — fastest serving path, CDN-friendly.

**Free-tier recommendations.** Cloudflare R2: 10 GB forever, zero egress (best for repeatedly serving PDFs to parents' phones). Backblaze B2: 10 GB + 1 GB/day egress. MinIO self-hosted: free if you already run a VM. AWS S3: 5 GB for 12 months only — not permanently free.

---

## `StoredFile`

[`StoredFile`](../../backend/src/main/java/in/schoolapp/storage/dto/StoredFile.java) is a record `{key, url, sizeBytes, contentType}`. Call sites stash `key` (for later `delete` or fresh `presignedUrl` calls) and embed `url` in WhatsApp messages / API responses.

---

## Endpoints

| Path | Method | `@PreAuthorize` |
|---|---|---|
| `/files/{*key}` | GET | none (public — only mounted when `provider=LOCAL`) |

Defined in [`FileController`](../../backend/src/main/java/in/schoolapp/storage/FileController.java). Explicitly permitted in [`SecurityConfig`](../../backend/src/main/java/in/schoolapp/config/SecurityConfig.java) via `requestMatchers(HttpMethod.GET, "/files/**").permitAll()`. Unauthenticated by design: receipt + report-card URLs are embedded in WhatsApp messages sent to parents, who don't have JWTs. Path segments contain unguessable UUIDs (`receipts/{tenantId}/{paymentId}.pdf`), which is acceptable for dev + Phase-1 production. Short-lived signed path tokens could tighten this later.

No endpoints are served by the `S3` provider — when S3 is active, URLs point directly at the S3 endpoint (presigned or public), and `FileController` is not loaded at all.

---

## Design decisions

- **Interface-first, provider switch at boot.** `@ConditionalOnProperty` on both implementations. Changing providers is one config line; no code knows which is active.
- **Key-traversal protection in `LOCAL`.** `LocalFileStorageService` runs two defences: up-front rejection of keys containing `..`, leading `/`, or backslashes; and a `Path.normalize().startsWith(baseDir)` post-resolve check. Both together defeat URL-encoded traversal, backslash traversal, and symlink-style escape attempts.
- **`delete` is best-effort.** Orphaned files are recoverable; a failed delete that propagates would roll back the owning entity's soft-delete transaction and leave the system in an inconsistent state. Both backends log and swallow.
- **`retrieve` over re-fetch by URL.** `MigrationProcessor` used to re-read uploaded images over HTTP to get the bytes back; adding `retrieve(key)` made that an in-process call and cut a serialisation + network round-trip.
- **`presignedUrl` TTL capped at 7 days.** S3's hard maximum. Code clamps rather than erroring so call sites can request "effectively forever" with `Duration.ofDays(365)` and get the longest valid value.
- **LOCAL ignores the TTL.** The URL is a stable path served by `FileController`; there's no signing mechanism. Callers assume the link stays valid as long as the file is on disk.

---

## Used by

- [`ReceiptService`](../../backend/src/main/java/in/schoolapp/fee/ReceiptService.java) — stores fee-receipt PDFs, passes the URL into WhatsApp receipt messages.
- [`ReportCardService`](../../backend/src/main/java/in/schoolapp/academics/ReportCardService.java) — stores report-card PDFs, URL embedded in the parent WhatsApp message and stamped onto the `report_cards` row.
- [`MigrationJobService`](../../backend/src/main/java/in/schoolapp/migration/MigrationJobService.java) and [`MigrationProcessor`](../../backend/src/main/java/in/schoolapp/migration/MigrationProcessor.java) — stores uploaded register images and retrieves bytes for OCR processing.
- [`StudentService`](../../backend/src/main/java/in/schoolapp/student/StudentService.java) — student photo + `student_documents` rows (V5).
- [`SchoolService`](../../backend/src/main/java/in/schoolapp/school/SchoolService.java) — school logo.

All callers depend on the `FileStorageService` interface only. None imports the `Local*` or `S3*` implementation.

---

## Cross-module calls

- **Reads / writes:** nothing (leaf module).
- **Called by:** `fee`, `academics`, `migration`, `student`, `school`.
- **Publishes events:** none.
- **Consumes events:** none.

---

## Migrations

The storage module itself owns no tables — keys + URLs are stored on the entities that own the files. Relevant consumer migrations:

- **V1** — TEXT columns stashing `FileStorageService` URLs: `schools.logo_url`, `students.photo_url`, `fee_payments.receipt_pdf_url`, `report_cards.pdf_url` (plus `wa_sent_at` for the delivery-status update path).
- **V5** — `student_documents` table (`storage_key`, `file_url`, `content_type`, `size_bytes`, `doc_type`, `uploaded_by_id`). Backed by the same `FileStorageService` abstraction — the table just records the key + metadata so documents can be listed and downloaded.
