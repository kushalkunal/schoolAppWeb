package in.schoolapp.billing.usage;

/**
 * Per-tenant metrics tracked against plan limits.
 *
 * <p>{@code periodGranularity} drives the {@code period_key} format in the {@code usage_counters}
 * table:
 * <ul>
 *   <li>{@link Granularity#MONTHLY} — period_key = {@code 'YYYY-MM'}. Resets at month
 *       boundary; the scheduled job creates new counters lazily.</li>
 *   <li>{@link Granularity#ALL_TIME} — period_key = {@code 'ALL'}. Single growing counter.</li>
 * </ul>
 *
 * <p>Names persisted as strings — never rename.
 */
public enum UsageMetric {
    MESSAGES_SENT_MONTHLY(Granularity.MONTHLY),
    OCR_PAGES_MONTHLY(Granularity.MONTHLY),
    STORAGE_BYTES(Granularity.ALL_TIME),
    STUDENTS_COUNT(Granularity.ALL_TIME),
    STAFF_COUNT(Granularity.ALL_TIME);

    public enum Granularity { MONTHLY, ALL_TIME }

    private final Granularity granularity;

    UsageMetric(Granularity granularity) {
        this.granularity = granularity;
    }

    public Granularity granularity() {
        return granularity;
    }
}
