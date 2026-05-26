package in.schoolapp.billing;

import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.entity.SubscriptionEvent;
import in.schoolapp.billing.entity.SubscriptionStatus;
import in.schoolapp.billing.repository.SubscriptionEventRepository;
import in.schoolapp.billing.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Daily 03:00 IST cron that transitions {@code PAST_DUE} subscriptions older than
 * {@code app.billing.grace-days} (default 7) to {@code SUSPENDED}.
 *
 * <p>"Older than" means: the most recent {@link SubscriptionEvent} whose {@code toStatus}
 * is {@code PAST_DUE} is more than the grace-window in the past. So a fresh
 * {@code invoice.payment_failed} webhook starts the grace clock; if Stripe retries and a
 * subsequent payment succeeds before grace expires, the status flips back to ACTIVE and the
 * scheduler stops looking at it.
 *
 * <p>Setting {@code app.billing.grace-days} ≤ 0 disables auto-suspension.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GracePeriodScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventRepository eventRepository;

    @Value("${app.billing.grace-days:7}")
    private int graceDays;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void enforceGracePeriod() {
        if (graceDays <= 0) {
            log.debug("Grace-period scheduler disabled (grace-days <= 0)");
            return;
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(graceDays);
        List<Subscription> pastDue = subscriptionRepository
            .findByStatus(SubscriptionStatus.PAST_DUE, PageRequest.of(0, 10_000))
            .getContent();

        int flipped = 0;
        for (Subscription sub : pastDue) {
            // Find the latest event that put this sub into PAST_DUE.
            OffsetDateTime since = eventRepository
                .findBySchoolIdOrderByCreatedAtDesc(sub.getSchoolId()).stream()
                .filter(e -> "PAST_DUE".equals(e.getToStatus()))
                .map(SubscriptionEvent::getCreatedAt)
                .findFirst()
                .orElse(null);
            // If we can't find the transition timestamp, fall back to subscription updated_at.
            if (since == null) since = sub.getUpdatedAt();
            if (since == null) since = sub.getCreatedAt();

            if (since != null && since.isBefore(cutoff)) {
                sub.setStatus(SubscriptionStatus.SUSPENDED);
                sub.setSuspensionReason("Auto-suspended after " + graceDays
                    + "-day PAST_DUE grace window expired");
                subscriptionRepository.save(sub);

                SubscriptionEvent ev = new SubscriptionEvent();
                ev.setSchoolId(sub.getSchoolId());
                ev.setSubscriptionId(sub.getId());
                ev.setEventType("AUTO_SUSPENDED");
                ev.setFromStatus("PAST_DUE");
                ev.setToStatus("SUSPENDED");
                ev.setNote("Grace window expired " + since);
                eventRepository.save(ev);
                flipped++;
            }
        }
        if (flipped > 0) {
            log.info("GracePeriodScheduler suspended {} subscriptions past their {}-day grace window",
                flipped, graceDays);
        }
    }
}
