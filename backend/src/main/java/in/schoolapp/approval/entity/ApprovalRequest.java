package in.schoolapp.approval.entity;

import in.schoolapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A pending (or decided) maker-checker request. The proposed change is <em>staged</em> here and
 * applied to the domain only when a different user approves it — so an unapproved discount or
 * refund never affects an invoice.
 * <p>
 * Two staging shapes, depending on the action:
 * <ul>
 *   <li>{@link #subjectId} — the id of an already-persisted-but-inactive domain row the handler
 *       just flips live (e.g. a {@code FeeDiscount} created with {@code active=false}).</li>
 *   <li>{@link #payloadJson} — serialized parameters for an action that has no natural staging
 *       row (e.g. a refund: paymentId + amount + reason), executed by the handler on approval.</li>
 * </ul>
 * Tenant-scoped (extends {@link BaseEntity}); the V37 row-level-security policy applies.
 */
@Entity
@Table(name = "approval_requests")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRequest extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ApprovalType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    /** Domain row this request acts on (handler-specific); null for payload-only requests. */
    @Column(name = "subject_id")
    private UUID subjectId;

    /** Serialized staged parameters for payload-backed requests; null for subject-backed ones. */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    /** Money magnitude of the request, for display and (future) threshold routing. */
    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    /** Human-readable one-liner for the approvals inbox. */
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "requested_by_id", nullable = false)
    private UUID requestedById;

    @Column(name = "requested_by_role", length = 30)
    private String requestedByRole;

    @Column(name = "decided_by_id")
    private UUID decidedById;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;
}
