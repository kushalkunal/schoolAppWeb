package in.schoolapp.billing;

import in.schoolapp.billing.entity.Plan;
import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.entity.SubscriptionEvent;
import in.schoolapp.billing.entity.SubscriptionStatus;
import in.schoolapp.billing.repository.SubscriptionEventRepository;
import in.schoolapp.billing.repository.SubscriptionRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionEventRepository subscriptionEventRepository;
    @Mock PlanService planService;

    @InjectMocks SubscriptionService service;

    private UUID schoolId;
    private Plan freePlan;
    private Plan growthPlan;

    @BeforeEach
    void setUp() {
        // Inject @Value-driven fields directly — Mockito doesn't read application.yml.
        ReflectionTestUtils.setField(service, "trialDays", 14);
        ReflectionTestUtils.setField(service, "defaultPlanCode", "FREE");

        schoolId = UUID.randomUUID();

        freePlan = new Plan();
        freePlan.setId(UUID.randomUUID());
        freePlan.setCode("FREE");

        growthPlan = new Plan();
        growthPlan.setId(UUID.randomUUID());
        growthPlan.setCode("GROWTH");
    }

    @Test
    void startTrialForSchool_createsTrialSubscriptionOnDefaultPlan() {
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.empty());
        when(planService.getPlanByCode("FREE")).thenReturn(freePlan);
        when(subscriptionRepository.save(any(Subscription.class)))
            .thenAnswer(inv -> {
                Subscription s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });

        Subscription out = service.startTrialForSchool(schoolId);

        assertThat(out.getSchoolId()).isEqualTo(schoolId);
        assertThat(out.getPlanId()).isEqualTo(freePlan.getId());
        assertThat(out.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(out.getTrialEndsAt()).isAfter(OffsetDateTime.now().plusDays(13));
        // One event row written for the audit trail.
        ArgumentCaptor<SubscriptionEvent> ev = ArgumentCaptor.forClass(SubscriptionEvent.class);
        verify(subscriptionEventRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("TRIAL_STARTED");
        assertThat(ev.getValue().getToStatus()).isEqualTo("TRIAL");
    }

    @Test
    void startTrialForSchool_refusesWhenAlreadyExists() {
        when(subscriptionRepository.findBySchoolId(schoolId))
            .thenReturn(Optional.of(new Subscription()));
        assertThatThrownBy(() -> service.startTrialForSchool(schoolId))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void suspend_writesEventAndFlipsStatus() {
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, freePlan);
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription out = service.suspend(schoolId, "Non-payment");

        assertThat(out.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(out.getSuspensionReason()).isEqualTo("Non-payment");
        ArgumentCaptor<SubscriptionEvent> ev = ArgumentCaptor.forClass(SubscriptionEvent.class);
        verify(subscriptionEventRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("SUSPENDED");
        assertThat(ev.getValue().getFromStatus()).isEqualTo("ACTIVE");
        assertThat(ev.getValue().getToStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void suspend_isIdempotent() {
        Subscription sub = subscription(SubscriptionStatus.SUSPENDED, freePlan);
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(sub));

        Subscription out = service.suspend(schoolId, "again");

        assertThat(out.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        verify(subscriptionEventRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void resume_movesToActive_whenTrialExpired() {
        Subscription sub = subscription(SubscriptionStatus.SUSPENDED, freePlan);
        sub.setTrialEndsAt(OffsetDateTime.now().minusDays(1));  // trial expired
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription out = service.resume(schoolId, null);
        assertThat(out.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void resume_movesToTrial_whenTrialStillActive() {
        Subscription sub = subscription(SubscriptionStatus.SUSPENDED, freePlan);
        sub.setTrialEndsAt(OffsetDateTime.now().plusDays(3));
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription out = service.resume(schoolId, null);
        assertThat(out.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
    }

    @Test
    void changePlan_updatesPlanIdAndWritesEvent() {
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, freePlan);
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(sub));
        when(planService.getPlan(freePlan.getId())).thenReturn(freePlan);
        when(planService.getPlanByCode("GROWTH")).thenReturn(growthPlan);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription out = service.changePlan(schoolId, "GROWTH", "upgrade");
        assertThat(out.getPlanId()).isEqualTo(growthPlan.getId());
        ArgumentCaptor<SubscriptionEvent> ev = ArgumentCaptor.forClass(SubscriptionEvent.class);
        verify(subscriptionEventRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("PLAN_CHANGED");
        assertThat(ev.getValue().getFromPlanCode()).isEqualTo("FREE");
        assertThat(ev.getValue().getToPlanCode()).isEqualTo("GROWTH");
    }

    @Test
    void effectiveStatus_returnsSuspendedWhenNoRow() {
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.empty());
        assertThat(service.effectiveStatus(schoolId)).isEqualTo(SubscriptionStatus.SUSPENDED);
    }

    // --- helper ---

    private Subscription subscription(SubscriptionStatus status, Plan plan) {
        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID());
        sub.setSchoolId(schoolId);
        sub.setPlanId(plan.getId());
        sub.setStatus(status);
        return sub;
    }
}
