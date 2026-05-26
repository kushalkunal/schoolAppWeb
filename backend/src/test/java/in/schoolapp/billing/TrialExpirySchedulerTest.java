package in.schoolapp.billing;

import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.entity.SubscriptionEvent;
import in.schoolapp.billing.entity.SubscriptionStatus;
import in.schoolapp.billing.repository.SubscriptionEventRepository;
import in.schoolapp.billing.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialExpirySchedulerTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionEventRepository subscriptionEventRepository;

    @InjectMocks TrialExpiryScheduler scheduler;

    @Test
    void expireOverdueTrials_transitionsExpired_keepsFresh() {
        Subscription expired = trial(OffsetDateTime.now().minusHours(1));
        Subscription fresh   = trial(OffsetDateTime.now().plusDays(3));
        Page<Subscription> page = new PageImpl<>(List.of(expired, fresh));
        when(subscriptionRepository.findByStatus(eq(SubscriptionStatus.TRIAL), any(Pageable.class)))
            .thenReturn(page);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.expireOverdueTrials();

        assertThat(expired.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(fresh.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);

        // One event row for the expired transition; none for the fresh.
        ArgumentCaptor<SubscriptionEvent> ev = ArgumentCaptor.forClass(SubscriptionEvent.class);
        verify(subscriptionEventRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("TRIAL_EXPIRED");
        assertThat(ev.getValue().getFromStatus()).isEqualTo("TRIAL");
        assertThat(ev.getValue().getToStatus()).isEqualTo("PAST_DUE");
    }

    @Test
    void expireOverdueTrials_noOpWhenAllFresh() {
        Page<Subscription> page = new PageImpl<>(List.of(trial(OffsetDateTime.now().plusDays(10))));
        when(subscriptionRepository.findByStatus(eq(SubscriptionStatus.TRIAL), any(Pageable.class)))
            .thenReturn(page);

        scheduler.expireOverdueTrials();

        verify(subscriptionRepository, never()).save(any());
        verify(subscriptionEventRepository, never()).save(any());
    }

    @Test
    void expireOverdueTrials_ignoresNullTrialEndsAt() {
        // Defensive — a TRIAL with no end date shouldn't trip the transition.
        Subscription odd = trial(null);
        Page<Subscription> page = new PageImpl<>(List.of(odd));
        when(subscriptionRepository.findByStatus(eq(SubscriptionStatus.TRIAL), any(Pageable.class)))
            .thenReturn(page);

        scheduler.expireOverdueTrials();
        assertThat(odd.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
        verify(subscriptionEventRepository, never()).save(any());
    }

    private Subscription trial(OffsetDateTime endsAt) {
        Subscription s = new Subscription();
        s.setId(UUID.randomUUID());
        s.setSchoolId(UUID.randomUUID());
        s.setPlanId(UUID.randomUUID());
        s.setStatus(SubscriptionStatus.TRIAL);
        s.setTrialEndsAt(endsAt);
        return s;
    }
}
