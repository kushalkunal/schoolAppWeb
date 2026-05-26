/**
 * Per-tenant usage metering against plan limits.
 *
 * <h2>What's wired in slice 1</h2>
 * <ul>
 *   <li>{@link in.schoolapp.billing.usage.entity.UsageCounter} entity + table.</li>
 *   <li>{@link in.schoolapp.billing.usage.UsageService} — atomic increment + limit enforcement
 *       APIs ready to be called.</li>
 *   <li>Platform-admin endpoints expose a snapshot per school.</li>
 * </ul>
 *
 * <h2>Deferred (slice 2)</h2>
 * The listeners that actually increment counters when business events happen are not yet
 * subscribed, to keep slice 1 from rippling into the existing 14 modules and risking the
 * green-test invariant. The intended wiring is:
 *
 * <pre>
 *   NotificationLog QUEUED     →  increment MESSAGES_SENT_MONTHLY
 *   FileStorageService.store   →  increment STORAGE_BYTES by file size
 *   MigrationJob COMMITTED     →  increment OCR_PAGES_MONTHLY by recordCount
 *   StudentCreatedEvent        →  increment STUDENTS_COUNT
 *   StaffCreatedEvent          →  increment STAFF_COUNT
 * </pre>
 *
 * <p>Pre-flight enforcement should call
 * {@link in.schoolapp.billing.usage.UsageService#enforceLimit(java.util.UUID,
 * in.schoolapp.billing.usage.UsageMetric)} <em>before</em> the work, so a tenant on
 * {@code FREE} cannot create their 51st student.
 */
package in.schoolapp.billing.usage;
