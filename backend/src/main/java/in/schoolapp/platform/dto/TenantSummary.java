package in.schoolapp.platform.dto;

import in.schoolapp.billing.entity.Subscription;
import in.schoolapp.billing.entity.SubscriptionStatus;
import in.schoolapp.school.entity.School;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight projection of one tenant for the platform-admin list view.
 */
public record TenantSummary(
    UUID schoolId,
    String name,
    String state,
    String board,
    String phone,
    String email,
    boolean active,
    UUID subscriptionId,
    String planCode,
    SubscriptionStatus status,
    OffsetDateTime trialEndsAt,
    OffsetDateTime createdAt
) {
    public static TenantSummary of(School s, Subscription sub, String planCode) {
        return new TenantSummary(
            s.getId(),
            s.getName(),
            s.getState(),
            s.getBoard() == null ? null : s.getBoard().name(),
            s.getPhone(),
            s.getEmail(),
            s.isActive(),
            sub == null ? null : sub.getId(),
            planCode,
            sub == null ? null : sub.getStatus(),
            sub == null ? null : sub.getTrialEndsAt(),
            s.getCreatedAt()
        );
    }
}
