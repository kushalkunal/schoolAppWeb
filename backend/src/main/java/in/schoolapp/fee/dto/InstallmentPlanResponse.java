package in.schoolapp.fee.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InstallmentPlanResponse(
    UUID planId,
    UUID parentInvoiceId,
    List<UUID> childInvoiceIds,
    OffsetDateTime createdAt
) {}
