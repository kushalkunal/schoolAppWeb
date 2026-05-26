# `fee` module

Fee-head catalog, invoices with partial payments, zero-config quick-collect, receipt PDFs, online-payment webhook reconciliation, dashboard, defaulters, and a daily reminder scheduler.

**Package:** `in.schoolapp.fee`

---

## Entities

| Entity | Table | Notes |
|---|---|---|
| [`FeeHead`](../../backend/src/main/java/in/schoolapp/fee/entity/FeeHead.java) | `fee_heads` | Per-tenant catalog ("Tuition", "Transport"). Unique per `(school_id, name)`. Soft-deleted via `is_active=false` to preserve FKs from historical payments. |
| [`FeeInvoice`](../../backend/src/main/java/in/schoolapp/fee/entity/FeeInvoice.java) | `fee_invoices` | `amount_due_paise` + `amount_paid_paise` accumulate; `applyPayment()` flips status `PENDING`→`PARTIAL`→`PAID`. Opening balances from paper ledgers are tagged `is_opening_balance=true`. |
| [`FeePayment`](../../backend/src/main/java/in/schoolapp/fee/entity/FeePayment.java) | `fee_payments` | The accounting truth. Unique `(school_id, receipt_number)`. `invoice_id` is nullable (quick-collect). `provider_reference` is unique when present — the webhook idempotency key (V3). `is_historical=true` marks OCR-migrated paper receipts. |
| [`FeeReminderSchedule`](../../backend/src/main/java/in/schoolapp/fee/entity/FeeReminderSchedule.java) | `fee_reminder_schedules` | One row per rule in the escalation ladder (e.g. "5 before due", "on due", "3/7/14 after due"). Unique per `(school_id, trigger_type, days_offset)`. |
| [`PaymentMode`](../../backend/src/main/java/in/schoolapp/fee/entity/PaymentMode.java) | enum | `CASH`, `ONLINE`, `CHEQUE`, `DD`, `BANK_TRANSFER` |
| [`InvoiceStatus`](../../backend/src/main/java/in/schoolapp/fee/entity/InvoiceStatus.java) | enum | `PENDING`, `PARTIAL`, `PAID`, `WAIVED` |
| [`ReminderTriggerType`](../../backend/src/main/java/in/schoolapp/fee/entity/ReminderTriggerType.java) | enum | `BEFORE_DUE`, `ON_DUE`, `AFTER_DUE` |

---

## Services

### [`FeeHeadService`](../../backend/src/main/java/in/schoolapp/fee/FeeHeadService.java)
Full CRUD for the per-tenant fee-head catalog.
- `create(tenantId, req)` — name uniqueness check, audit, persist.
- `list(tenantId)` — active only, ordered by name.
- `update(tenantId, id, req)` — rename; collision against another active head is a validation error.
- `deactivate(tenantId, id)` — soft delete (`active=false`) so historical `FeePayment` / `FeeInvoice` FKs remain valid.

### [`FeeInvoiceService`](../../backend/src/main/java/in/schoolapp/fee/FeeInvoiceService.java)
- `createInvoice(tenantId, req)` — one invoice, starts `PENDING`.
- `recordOpeningBalances(tenantId, req)` — bulk import from paper registers; each entry becomes an invoice with `is_opening_balance=true`, description defaulting to "Opening balance (carried forward)".
- `listInvoicesForStudent(studentId)` / `getOutstanding(studentId)` — read-side helpers.
- Package-private helpers `getInvoiceOrThrow`, `findPendingForStudent` (ordered for FIFO application), `save` — consumed by `FeePaymentService`.

### [`FeePaymentService`](../../backend/src/main/java/in/schoolapp/fee/FeePaymentService.java)
Source of payment truth. Writes every payment through the same pipeline: apply-to-invoices → generate receipt number → persist → render PDF → publish event.
- `quickCollect(tenantId, req)` — LLD §5.3 zero-config path. If `invoiceId` is provided, applies to that invoice only (rejects overpayment). Otherwise applies **FIFO** across the student's pending invoices; excess becomes a standalone payment (advance). Fires `FeePaymentCreatedEvent` so `ReceiptDeliveryListener` can WhatsApp the PDF after commit. Audit-logged via `AuditLogger.logCreate`.
- `createOnlinePayment(tenantId, studentId, amountPaise, providerReference, providerPaymentMethod)` — called by [`PaymentEventListener`](../../backend/src/main/java/in/schoolapp/payment/PaymentEventListener.java) on a verified `PAID` webhook. **Idempotent by `provider_reference`** (V3 unique index): a repeated webhook returns the existing row. Applies FIFO like quickCollect, records `payment_mode=ONLINE`, renders the PDF, audit-logs, and fires the same `FeePaymentCreatedEvent` — closing the "parent taps link → pays → receipt arrives" loop with no admin action.
- `createHistorical(...)` — migration commit flow. Receipt number is prefixed `HIST-` so it's visually distinct. **Publishes no event** (the parent already has the original paper receipt; only the audit trail is being digitised).
- `getPayment(tenantId, id)` / `getStudentSummary(tenantId, studentId)` — reads. Summary returns outstanding + last 20 payments + full invoice list.

### [`ReceiptService`](../../backend/src/main/java/in/schoolapp/fee/ReceiptService.java)
- `nextSequence(tenantId)` — atomic increment of `schools.settings->>'receiptSequence'` via a native `UPDATE … RETURNING` JSONB path update. Safe under concurrent collections.
- `formatReceiptNumber(seq, year)` — pure `REC-{year}-{6-digit-seq}`.
- `generatePdf(payment, student, school)` — OpenPDF (LGPL) A4 single-page layout: school name + city/state, receipt number + date, student name + admission number, payment mode, prominent ₹ amount, footer.
- `storeReceipt(tenantId, paymentId, bytes)` — persists to `receipts/{tenantId}/{paymentId}.pdf` via `FileStorageService`.
- `formatAmount(paise)` — paise-to-rupees formatter (never uses `double` for money).

### [`FeeDashboardService`](../../backend/src/main/java/in/schoolapp/fee/FeeDashboardService.java)
Aggregated read-side. All queries are `school_id`-scoped; no cross-tenant leakage possible even through native SQL.
- `getDashboard(tenantId)` — collected today / this month, outstanding total, overdue total, defaulter count, payment count today.
- `listDefaulters(tenantId, page, size)` — paginated native query for the outstanding aggregate, then in-memory join with student name + current-enrollment class/section + `daysOverdue` derived from `oldestDueDate`.

### [`FeeReminderService`](../../backend/src/main/java/in/schoolapp/fee/FeeReminderService.java)
- `queueBulkReminders(tenantId, req)` — entry point for manual batches and for the scheduler.
- `dispatchReminders(tenantId, req)` — `@Async("notificationExecutor")`. For each student: skip if outstanding is zero, resolve primary parent, build a payment link via `PaymentLinkService`, render the WhatsApp body via `MessageTemplateService.feeReminder`, send. Graceful degradation: if link creation throws, the reminder still goes out without a link.

### [`FeeReminderSchedulerService`](../../backend/src/main/java/in/schoolapp/fee/FeeReminderSchedulerService.java)
- `run()` — `@Scheduled(cron = "0 30 9 * * *", zone = "Asia/Kolkata")` — **daily 09:30 IST**, before morning break, so parents see it early. Iterates active tenants.
- `scanTenant(tenantId, today)` — for each active `FeeReminderSchedule`, computes the target due-date from `triggerType` + `daysOffset` (BEFORE_DUE → `today + offset`, ON_DUE → `today`, AFTER_DUE → `today - offset`), looks up students with invoices due on that date, and fires `dispatchReminders`. Dedupes students across overlapping schedules so "3 after" and "5 after" don't double-message the same family. Also the public entrypoint for the admin "run now" button.

---

## Endpoints

| Method | Path | `@PreAuthorize` |
|---|---|---|
| POST | `/api/v1/tenants/{tenantId}/fees/payments` | `FEE_WRITER` |
| GET | `/api/v1/tenants/{tenantId}/fees/payments/{paymentId}` | JWT |
| POST | `/api/v1/tenants/{tenantId}/fees/invoices` | `FEE_WRITER` |
| POST | `/api/v1/tenants/{tenantId}/fees/opening-balances` | `FEE_WRITER` |
| GET | `/api/v1/tenants/{tenantId}/students/{studentId}/fee-summary` | JWT |
| GET | `/api/v1/tenants/{tenantId}/fees/dashboard` | JWT |
| GET | `/api/v1/tenants/{tenantId}/fees/defaulters?page=&size=` | JWT |
| POST | `/api/v1/tenants/{tenantId}/fees/reminders` | `FEE_WRITER` |
| POST | `/api/v1/tenants/{tenantId}/fee-heads` | `FEE_WRITER` |
| GET | `/api/v1/tenants/{tenantId}/fee-heads` | JWT |
| PUT | `/api/v1/tenants/{tenantId}/fee-heads/{id}` | `FEE_WRITER` |
| DELETE | `/api/v1/tenants/{tenantId}/fee-heads/{id}` | `FEE_WRITER` |
| GET | `/api/v1/tenants/{tenantId}/fee-reminder-schedules` | JWT |
| POST | `/api/v1/tenants/{tenantId}/fee-reminder-schedules` | `FEE_WRITER` |
| PUT | `/api/v1/tenants/{tenantId}/fee-reminder-schedules/{id}` | `FEE_WRITER` |
| DELETE | `/api/v1/tenants/{tenantId}/fee-reminder-schedules/{id}` | `FEE_WRITER` |
| POST | `/api/v1/tenants/{tenantId}/fee-reminder-schedules/run-now` | `FEE_WRITER` |

`FEE_WRITER` = `SCHOOL_OWNER`, `PRINCIPAL`, `ADMIN`, `ACCOUNTANT`.

---

## Key design decisions

- **Money is stored in paise** (integer). No `double` anywhere — controllers convert to ₹ only at the edge.
- **Quick-collect has no pre-config dependency.** Type student + amount + mode → receipt. If invoices exist, FIFO-apply; if not, the payment stands alone. Overpayment against a **specific** invoice is rejected; overpayment in FIFO mode is accepted as an advance.
- **Receipt numbers are a per-tenant atomic sequence** stored in `schools.settings.receiptSequence` JSONB. The native `UPDATE … jsonb_set … RETURNING` path is the one place we care about races.
- **Online payments are idempotent by `provider_reference`** (V3 partial unique index). Webhook retries from BSPs are aggressive — a duplicate delivery must be a no-op, not a duplicate row. This is the single guarantee that keeps the "pay online" flow safe.
- **Historical (paper-receipt) payments fire no event** — the parent already holds the paper original; re-sending a WhatsApp for a digitisation step would be confusing.
- **Fee heads are soft-deleted** (`is_active=false`) so historical invoices/payments with a `fee_head_id` remain referential.
- **Reminder schedules dedupe per tenant per cron run** — overlapping rules ("3 after", "5 after") targeting the same invoice won't double-message.
- **Scheduler runs at 09:30 IST** — early enough that parents see the reminder before heading to work, late enough that nobody's still asleep.
- **Payment-link failures don't abort reminder batches** — if `PaymentLinkService` throws for a provider outage, the reminder still goes out without a link. Better a link-less reminder than no reminder.
- **Every write path is audit-logged** (`AuditLogger.logCreate`/`logUpdate`/`logDelete`) — `quickCollect`, `createOnlinePayment`, fee-head CRUD, reminder-schedule CRUD.

---

## Relationships to other modules

- **Reads from `student`:** `StudentService.getStudentEntity`, `FamilyService.getPrimaryParent`.
- **Reads from `school`:** `SchoolService.getSchoolEntity` (name/city for receipt PDF + `settings.receiptSequence` for numbering), `SchoolClassRepository` / `SectionRepository` (defaulters enrichment).
- **Reads from `storage`:** `FileStorageService.store` for receipt PDFs.
- **Reads from `communication`:** `WhatsAppNotifier`, `MessageTemplateService`.
- **Reads from `payment`:** `PaymentLinkService.createLink` for reminder URLs.
- **Reads from `audit`:** `AuditLogger`.
- **Written to by:** `payment` via `PaymentEventListener` calling `FeePaymentService.createOnlinePayment` on PAID webhooks; `migration` via `FeePaymentService.createHistorical`.
- **Events published:** [`FeePaymentCreatedEvent`](../../backend/src/main/java/in/schoolapp/fee/event/FeePaymentCreatedEvent.java) — consumed by `ReceiptDeliveryListener` (communication) for WhatsApp receipt delivery.
- **Events consumed:** none directly (inbound webhook lands on `PaymentEventListener` in the `payment` module, which then calls into here).

---

## Related migrations

- **V1** — `fee_heads` (`UNIQUE(school_id, name)`), `fee_invoices` (with `idx_fee_invoices_student`, `idx_fee_invoices_school_status`, `idx_fee_invoices_outstanding`), `fee_payments` (`UNIQUE(school_id, receipt_number)`, `idx_fee_payments_student`, `idx_fee_payments_school_date`), `fee_reminder_schedules` (`UNIQUE(school_id, trigger_type, days_offset)`, `idx_fee_schedules_school`). Receipt-sequence JSONB key `schools.settings.receiptSequence` is initialised to 0 at signup by `SchoolService.initialSettings`.
- **V3** — adds `fee_payments.provider_reference` (VARCHAR(100)) plus the partial unique index `uq_fee_payments_provider_reference` (where the column is non-null). This is the webhook idempotency key consumed by `FeePaymentService.createOnlinePayment`.
