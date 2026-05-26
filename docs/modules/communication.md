# `communication` module

WhatsApp + email dispatch, message templates, bulk circulars, inbound inbox, delivery audit log, and the BSP webhook. Every outbound message the app ever sends passes through here, and every reply a parent sends lands here.

**Package:** `in.schoolapp.communication`

---

## Purpose

All outbound parent communication (absence alerts, late-arrival alerts, fee receipts, fee reminders, report cards, circulars, OTPs) is routed through a single `WhatsAppNotifier` bean. A decorator (`AuditingWhatsAppNotifier`) wraps the concrete provider SPI (`RawWhatsAppNotifier`) and writes a row to `notification_log` for every dispatch attempt; the BSP webhook transitions that row through SENT → DELIVERED → READ (or FAILED) via delivery-status callbacks. Inbound replies from parents are routed by `WhatsAppInboxRoutingService` into `whatsapp_inbox_messages` and surfaced to the class teacher via `InboxService`. Bulk broadcasts (`CircularService`) fan out async to parents of a class/section/student set.

---

## Entities and tables

| Entity | Table | Notes |
|---|---|---|
| [`NotificationLog`](../../backend/src/main/java/in/schoolapp/communication/entity/NotificationLog.java) | `notification_log` (V1) | Audit trail for every outbound message; `wa_message_id` indexed for O(1) webhook status lookups. |
| [`NotificationStatus`](../../backend/src/main/java/in/schoolapp/communication/entity/NotificationStatus.java) | enum | `QUEUED → SENT → DELIVERED → READ` or `FAILED`. |
| [`WhatsAppInboxMessage`](../../backend/src/main/java/in/schoolapp/communication/entity/WhatsAppInboxMessage.java) | `whatsapp_inbox_messages` (V1) | One row per inbound parent reply; `routed_to_id` is the resolved class teacher (nullable for unknown-phone messages). |
| [`Circular`](../../backend/src/main/java/in/schoolapp/communication/entity/Circular.java) | `circulars` (V1) | Bulk broadcast history; `target_ids UUID[]` stores the class/section/student list. `sent_count/failed_count/delivered_count/read_count` updated by dispatch + webhook. |
| [`CircularTargetType`](../../backend/src/main/java/in/schoolapp/communication/entity/CircularTargetType.java) | enum | `ALL_PARENTS \| CLASSES \| SECTIONS \| STUDENTS`. |

---

## Dispatch pipeline

```
call site (AbsenceAlert / ReceiptDelivery / CircularService / AuthService.OTP)
        │
        │ WhatsAppMessage (body, type, optional Audit)
        ▼
[AuditingWhatsAppNotifier]  ── writes QUEUED row ──►  notification_log
        │
        ▼
[RawWhatsAppNotifier SPI]   ── one of:
    ├─ LoggingWhatsAppNotifier  (provider=LOGGING, default — dev safe)
    └─ WatiWhatsAppNotifier     (provider=WATI — WATI REST client)
        │
        │ returns BSP waMessageId (or null)
        ▼
[AuditingWhatsAppNotifier]  ── marks SENT + stores waMessageId ──►  notification_log
```

Failures in the raw SPI are caught and logged by the decorator, never rethrown — notifications are best-effort and must not roll back the business transaction that triggered them (payment commit, attendance save).

### Key services

| File | Key methods | Notes |
|---|---|---|
| [`AuditingWhatsAppNotifier`](../../backend/src/main/java/in/schoolapp/communication/AuditingWhatsAppNotifier.java) | `send(WhatsAppMessage)` | The public `WhatsAppNotifier` bean. Wraps the raw SPI; writes `notification_log` rows around every dispatch attempt. |
| [`NotificationLogger`](../../backend/src/main/java/in/schoolapp/communication/NotificationLogger.java) | `recordQueued`, `markSent`, `markDelivered`, `markRead`, `markFailed`, `markFailedByWaMessageId` | Every mutating method runs in `@Transactional(REQUIRES_NEW)` so audit failures never roll back the caller's business transaction. Exceptions are swallowed. |
| [`WhatsAppInboxRoutingService`](../../backend/src/main/java/in/schoolapp/communication/WhatsAppInboxRoutingService.java) | `route(waMessageId, fromPhone, body)` | Idempotent by `waMessageId`. Resolves phone → Parent → primary child → section → `classTeacherId`. Writes one inbox row per matched tenant. |
| [`InboxService`](../../backend/src/main/java/in/schoolapp/communication/InboxService.java) | `listForTeacher`, `listAllForTenant`, `listUnrouted`, `unreadCount`, `markRead`, `markResolved`, `reassign` | Read / triage API over `whatsapp_inbox_messages`. Creates never happen here — those go through the routing service from the webhook path. |
| [`CircularService`](../../backend/src/main/java/in/schoolapp/communication/CircularService.java) | `createAndDispatch`, `list`, `get`, `dispatchAsync` (async) | Persists the `Circular` row synchronously, then fans out recipients on `@Async("notificationExecutor")` after the outer transaction commits. |
| [`MessageTemplateService`](../../backend/src/main/java/in/schoolapp/communication/template/MessageTemplateService.java) | `absenceAlert`, `siblingAbsenceAlert`, `lateArrivalAlert`, `feeReceipt`, `feeReminder` | Plain-string templates. Central render site so swapping to BSP-approved template IDs later touches one file. |

### Dispatcher beans

| File | When active | Notes |
|---|---|---|
| [`RawWhatsAppNotifier`](../../backend/src/main/java/in/schoolapp/communication/dispatcher/RawWhatsAppNotifier.java) | SPI | Returns the BSP-assigned `waMessageId` for webhook correlation, or `null` if the provider doesn't issue one. Must throw on hard failure. |
| [`LoggingWhatsAppNotifier`](../../backend/src/main/java/in/schoolapp/communication/dispatcher/LoggingWhatsAppNotifier.java) | `app.whatsapp.provider=LOGGING` (default) | Dev-safe — logs the masked phone + body preview, returns null. |
| [`WatiWhatsAppNotifier`](../../backend/src/main/java/in/schoolapp/communication/dispatcher/WatiWhatsAppNotifier.java) | `app.whatsapp.provider=WATI` | Real HTTP client via Spring's `RestClient`. Parses WATI's `whatsappMessageId` from the response for webhook correlation. |
| [`DefaultOtpDispatcher`](../../backend/src/main/java/in/schoolapp/communication/dispatcher/DefaultOtpDispatcher.java) | always | Routes PHONE → `WhatsAppNotifier`, EMAIL → `EmailSender`. |
| [`LoggingEmailSender`](../../backend/src/main/java/in/schoolapp/communication/dispatcher/LoggingEmailSender.java) / [`SmtpEmailSender`](../../backend/src/main/java/in/schoolapp/communication/dispatcher/SmtpEmailSender.java) | `app.email.provider=LOGGING` / `=SMTP` | Mirrors the WhatsApp provider split. |

### Event listeners (AFTER_COMMIT, async)

| Listener | Consumes | Sends |
|---|---|---|
| [`ReceiptDeliveryListener`](../../backend/src/main/java/in/schoolapp/communication/event/ReceiptDeliveryListener.java) | `FeePaymentCreatedEvent` | Fee receipt template + receipt PDF URL to primary parent |
| [`ReportCardDeliveryListener`](../../backend/src/main/java/in/schoolapp/communication/event/ReportCardDeliveryListener.java) | `ReportCardGeneratedEvent` | Report card PDF URL; stamps `report_cards.wa_sent_at` on success |
| [`AbsenceAlertService`](../../backend/src/main/java/in/schoolapp/attendance/AbsenceAlertService.java) (in `attendance`) | `AttendanceSubmittedEvent` | Sibling-combined absence + per-child late alerts |

All three run on `@Async("notificationExecutor")` and use `@TransactionalEventListener(phase = AFTER_COMMIT)` so a rolled-back business transaction can never leak messages.

### `WhatsAppMessage` shape

[`WhatsAppMessage`](../../backend/src/main/java/in/schoolapp/communication/dispatcher/WhatsAppMessage.java) is a record with `toPhone`, `body`, optional `mediaUrl`, a `MessageType` enum (`ABSENCE_ALERT`, `LATE_ARRIVAL_ALERT`, `FEE_RECEIPT`, `FEE_REMINDER`, `CIRCULAR`, `REPORT_CARD`, `OTP`, `EMERGENCY`), and an optional `Audit` context (`schoolId`, `studentId`, `parentId`, `recipientName`). When `Audit` is absent or `schoolId` is null, `NotificationLogger.recordQueued` returns `Optional.empty()` — the call site has opted out of persistence (e.g. OTP dispatches where the staff row may not yet be known).

---

## Endpoints

| Path | Method | `@PreAuthorize` |
|---|---|---|
| `/api/v1/tenants/{tenantId}/circulars` | POST | `OWNER_OR_ADMIN` |
| `/api/v1/tenants/{tenantId}/circulars` | GET | (authenticated) |
| `/api/v1/tenants/{tenantId}/circulars/{id}` | GET | (authenticated) |
| `/api/v1/tenants/{tenantId}/inbox/mine` | GET | `ANY_TEACHER` |
| `/api/v1/tenants/{tenantId}/inbox/mine/unread-count` | GET | `ANY_TEACHER` |
| `/api/v1/tenants/{tenantId}/inbox/unrouted` | GET | `OWNER_OR_ADMIN` |
| `/api/v1/tenants/{tenantId}/inbox` | GET | `OWNER_OR_ADMIN` |
| `/api/v1/tenants/{tenantId}/inbox/{messageId}/read` | POST | `ANY_TEACHER` |
| `/api/v1/tenants/{tenantId}/inbox/{messageId}/resolve` | POST | `ANY_TEACHER` |
| `/api/v1/tenants/{tenantId}/inbox/{messageId}/reassign?toTeacherId=` | POST | `OWNER_OR_ADMIN` |
| `/webhooks/whatsapp` | POST | (HMAC-verified, permitted in `SecurityConfig`) |

Sources: [`CircularController`](../../backend/src/main/java/in/schoolapp/communication/CircularController.java), [`InboxController`](../../backend/src/main/java/in/schoolapp/communication/InboxController.java), [`WhatsAppWebhookController`](../../backend/src/main/java/in/schoolapp/communication/webhook/WhatsAppWebhookController.java).

---

## Webhook

[`WhatsAppWebhookController`](../../backend/src/main/java/in/schoolapp/communication/webhook/WhatsAppWebhookController.java) at `POST /webhooks/whatsapp`:

1. Verifies the `X-WA-Signature` header — HMAC-SHA256 of raw body against `app.whatsapp.webhook-secret`, constant-time compare. Missing or invalid signature → `401`.
2. Parses the Meta Cloud API shape (`entry[].changes[].value.statuses[]` and `...messages[]`). WATI and Interakt forward largely-compatible shapes.
3. Translates each `statuses[]` entry: `sent` is a no-op (the auditing decorator already marked SENT inline), `delivered` → `NotificationLogger.markDelivered`, `read` → `markRead`, `failed` → `markFailedByWaMessageId`.
4. Routes each `messages[]` entry through `WhatsAppInboxRoutingService.route`.
5. Always returns `200` on a verified body — body-parse errors are logged and swallowed so the BSP doesn't retry indefinitely.

---

## Design decisions

- **Decorator, not service injection.** Call sites inject `WhatsAppNotifier` and get auditing for free; they don't know `NotificationLogger` exists. Removing auditing = remove the decorator bean; adding retry/queueing = add another decorator in front.
- **`REQUIRES_NEW` for every log write.** `notification_log` rows must commit independently of the caller's transaction — otherwise a webhook-ack rollback would erase the record of a message we actually sent. This is also why logger failures are caught and logged rather than rethrown.
- **Circular dispatch is async and post-commit.** The `Circular` row is saved synchronously so the admin's history list reflects it immediately, but the per-parent fan-out runs on `notificationExecutor` after the outer `@Transactional` commits. Count columns (`sent_count`, `failed_count`) tick up as dispatch progresses.
- **Inbox writes only from the webhook.** `InboxService` never creates rows; all inserts come from `WhatsAppInboxRoutingService`. This keeps the write path idempotent (it dedupes by `waMessageId`) and the read path simple.
- **Unknown-phone inbound messages are dropped, not persisted.** Without a matching Parent row we can't tenant-bind the message, and persisting it globally would risk a cross-tenant leak when a principal views "unrouted". A future phone-number-id lookup in the webhook body (BSP-specific) would enable tenant binding for unknown phones.
- **Sibling combination happens in `attendance`, not here.** `AbsenceAlertService` buckets students by primary parent via `FamilyService.groupStudentsByPrimaryParent` and calls `MessageTemplateService.siblingAbsenceAlert` when a parent has 2+ absent children; the notifier itself stays dumb.

---

## Cross-module calls

- **Calls into:** `school.SchoolService` (school name for templates), `student.FamilyService` + `student.ParentRepository` + `student.StudentEnrollmentRepository` + `student.StudentParentLinkRepository` (recipient resolution), `school.SectionRepository` (class → section expansion for circulars), `fee.FeeInvoiceService` (outstanding balance for receipt template), `academics.ReportCardRepository` (stamping `wa_sent_at`), `audit.AuditLogger` (circular creation audit), `common.TenantContext` + `PhoneNormalizer`.
- **Called by:** `auth.OtpService` (via `OtpDispatcher`), `attendance.AbsenceAlertService` (via `WhatsAppNotifier`), `fee.FeeReminderService` (via `WhatsAppNotifier`, not shown here), plus event-driven consumption of `FeePaymentCreatedEvent` and `ReportCardGeneratedEvent`.
- **Publishes:** no events of its own. Everything arriving here is either a direct call or an event from another module.

---

## Migrations

- **V1** — `notification_log`, `whatsapp_inbox_messages`, `circulars` tables plus the partial index on `notification_log(wa_message_id)` used for webhook status lookups.
- **V3** — adds `idx_notif_recipient_phone(recipient_phone, created_at DESC)` so the inbox can find "the last message we sent this phone" for threading context. (The `wa_message_id` index from V1 remains unchanged.)
