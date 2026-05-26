package in.schoolapp.billing.usage;

import in.schoolapp.billing.PlanService;
import in.schoolapp.billing.SubscriptionService;
import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.usage.entity.UsageCounter;
import in.schoolapp.billing.usage.repository.UsageCounterRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock UsageCounterRepository repository;
    @Mock SubscriptionService subscriptionService;
    @Mock PlanService planService;

    @InjectMocks UsageService service;

    @Test
    void enforceLimit_allowsWhenUnlimited() {
        UUID schoolId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Subscription sub = new Subscription();
        sub.setSchoolId(schoolId);
        sub.setPlanId(planId);
        when(subscriptionService.getForSchool(schoolId)).thenReturn(sub);
        when(planService.limitFor(planId, "STUDENTS_COUNT")).thenReturn(-1L);  // unlimited

        assertThatCode(() -> service.enforceLimit(schoolId, UsageMetric.STUDENTS_COUNT))
            .doesNotThrowAnyException();
    }

    @Test
    void enforceLimit_allowsWhenBelowLimit() {
        UUID schoolId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Subscription sub = new Subscription();
        sub.setSchoolId(schoolId);
        sub.setPlanId(planId);
        when(subscriptionService.getForSchool(schoolId)).thenReturn(sub);
        when(planService.limitFor(planId, "STUDENTS_COUNT")).thenReturn(50L);

        UsageCounter counter = new UsageCounter();
        counter.setCountValue(10L);
        when(repository.findById(any(UsageCounter.Id.class))).thenReturn(Optional.of(counter));

        assertThatCode(() -> service.enforceLimit(schoolId, UsageMetric.STUDENTS_COUNT))
            .doesNotThrowAnyException();
    }

    @Test
    void enforceLimit_throwsWhenAtOrOverLimit() {
        UUID schoolId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Subscription sub = new Subscription();
        sub.setSchoolId(schoolId);
        sub.setPlanId(planId);
        when(subscriptionService.getForSchool(schoolId)).thenReturn(sub);
        when(planService.limitFor(planId, "STUDENTS_COUNT")).thenReturn(50L);

        UsageCounter counter = new UsageCounter();
        counter.setCountValue(50L);
        when(repository.findById(any(UsageCounter.Id.class))).thenReturn(Optional.of(counter));

        assertThatThrownBy(() -> service.enforceLimit(schoolId, UsageMetric.STUDENTS_COUNT))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(ErrorCode.PLAN_LIMIT_EXCEEDED);
    }

    @Test
    void increment_swallowsRepositoryException() {
        UUID schoolId = UUID.randomUUID();
        // Repo throws, but increment must not propagate — counters are best-effort.
        org.mockito.Mockito.doThrow(new RuntimeException("DB blew up"))
            .when(repository).atomicIncrement(any(), anyString(), anyString(), anyLong());

        assertThatCode(() -> service.increment(schoolId, UsageMetric.MESSAGES_SENT_MONTHLY, 1L))
            .doesNotThrowAnyException();
    }

    @Test
    void increment_skipsZeroDelta() {
        service.increment(UUID.randomUUID(), UsageMetric.MESSAGES_SENT_MONTHLY, 0L);
        verify(repository, org.mockito.Mockito.never())
            .atomicIncrement(any(), anyString(), anyString(), anyLong());
    }
}
