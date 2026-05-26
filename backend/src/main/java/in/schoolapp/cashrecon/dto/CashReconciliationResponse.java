package in.schoolapp.cashrecon.dto;

import in.schoolapp.cashrecon.entity.CashReconciliation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CashReconciliationResponse(
    UUID id,
    LocalDate closedOnDate,
    UUID closedById,
    long expectedCashPaise, long expectedUpiPaise, long expectedChequePaise, long expectedOtherPaise,
    long countedCashPaise,  long countedUpiPaise,  long countedChequePaise,  long countedOtherPaise,
    long variancePaise,
    String notes,
    OffsetDateTime closedAt
) {
    public static CashReconciliationResponse from(CashReconciliation r) {
        return new CashReconciliationResponse(
            r.getId(), r.getClosedOnDate(), r.getClosedById(),
            r.getExpectedCashPaise(), r.getExpectedUpiPaise(), r.getExpectedChequePaise(), r.getExpectedOtherPaise(),
            r.getCountedCashPaise(),  r.getCountedUpiPaise(),  r.getCountedChequePaise(),  r.getCountedOtherPaise(),
            r.getVariancePaise(), r.getNotes(), r.getClosedAt());
    }
}
