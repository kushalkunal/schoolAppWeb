package in.schoolapp.fee.structure.dto;

public record GenerateInvoicesResponse(
    int studentsProcessed,
    int invoicesCreated,
    int invoicesSkippedDuplicate,
    long totalAmountPaise
) {}
