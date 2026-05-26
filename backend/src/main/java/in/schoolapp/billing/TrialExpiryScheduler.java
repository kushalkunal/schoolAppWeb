package in.schoolapp.billing;

import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.entity.SubscriptionEvent;
import in.schoolapp.billing.entity.SubscriptionStatus;
import in.schoolapp.billing.repository.SubscriptionEventRepository;
import in.schoolapp.billing.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Daily 02:00 IST job that transitions {@code TRIAL} subscriptions whose
 * {@code trial_ends_at} is in the past to {@code PAST_DUE}.
 *
 * <p>Why PAST_DUE and not SUSPENDED: PAST_DUE keeps mutating endpoints working (the
 * {@link SubscriptionGuardInterceptor} allows it through). The intent is "your card hasn't
 * cleared yet but you can keep operating" — a soft grace window. A later slice can add a
 * second-stage scheduler that flips PAST_DUE → SUSPENDED after a configurable grace period
 * (e.g. 3 days), once Stripe Billing is wired and we have actual payment-failure signals.
 *
 * <p>Idempotent: running it twice in the same minute does no extra work because the second
 * pass finds no TRIAL rows past due.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventRepository subscriptionEventRepository;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void expireOverdueTrials() {
        OffsetDateTime now = OffsetDateTime.now();
        // Cheaper to scan all-by-status than to add a dedicated index for the cron — the row
        // count of TRIAL subscriptions stays bounded by the number of new schools per
        // trial-window. We can revisit if this grows past ~10k.
        List<Subscription> trials = subscriptionRepository
            .findByStatus(SubscriptionStatus.TRIAL,
                org.springframework.data.domain.PageRequest.of(0, 10_000))
            .getContent();

        int flipped = 0;
        for (Subscription sub : trials) {
            if (sub.getTrialEndsAt() != null && sub.getTrialEndsAt().isBefore(now)) {
                String from = sub.getStatus().name();
                sub.setStatus(SubscriptionStatus.PAST_DUE);
                subscriptionRepository.save(sub);

                SubscriptionEvent ev = new SubscriptionEvent();
                ev.setSchoolId(sub.getSchoolId());
                ev.setSubscriptionId(sub.getId());
                ev.setEventType("TRIAL_EXPIRED");
                ev.setFromStatus(from);
                ev.setToStatus("PAST_DUE");
                ev.setNote("Trial ended " + sub.getTrialEndsAt());
                subscriptionEventRepository.save(ev);
                flipped++;
            }
        }
        if (flipped > 0) {
            log.info("TrialExpiryScheduler: transitioned {} subscriptions TRIAL → PAST_DUE", flipped);
        }
    }
}
