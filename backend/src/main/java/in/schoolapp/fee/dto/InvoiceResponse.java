package in.schoolapp.fee.dto;

import in.schoolapp.fee.entity.FeeInvoice;
import in.schoolapp.fee.entity.InvoiceStatus;

import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    UUID studentId,
    UUID feeHeadId,
    long amountDuePaise,
    long amountPaidPaise,
    long balancePaise,
    LocalDate dueDate,
    InvoiceStatus status,
    boolean openingBalance,
    String description
) {
    public static InvoiceResponse from(FeeInvoice inv) {
        return new InvoiceResponse(
            inv.getId(),
            inv.getStudentId(),
            inv.getFeeHeadId(),
            inv.getAmountDuePaise(),
            inv.getAmountPaidPaise(),
            inv.balancePaise(),
            inv.getDueDate(),
            inv.getStatus(),
            inv.isOpeningBalance(),
            inv.getDescription()
        );
    }
}
