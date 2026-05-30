package in.schoolapp.approval.dto;

import in.schoolapp.approval.entity.ApprovalRequest;
import in.schoolapp.approval.entity.ApprovalStatus;
import in.schoolapp.approval.entity.ApprovalType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApprovalRequestResponse(
    UUID id,
    ApprovalType type,
    ApprovalStatus status,
    UUID subjectId,
    long amountPaise,
    String summary,
    UUID requestedById,
    UUID decidedById,
    OffsetDateTime decidedAt,
    String decisionNote,
    OffsetDateTime createdAt
) {
    public static ApprovalRequestResponse from(ApprovalRequest r) {
        return new ApprovalRequestResponse(
            r.getId(), r.getType(), r.getStatus(), r.getSubjectId(), r.getAmountPaise(),
            r.getSummary(), r.getRequestedById(), r.getDecidedById(), r.getDecidedAt(),
            r.getDecisionNote(), r.getCreatedAt());
    }
}
