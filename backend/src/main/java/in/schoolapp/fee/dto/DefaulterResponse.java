package in.schoolapp.fee.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DefaulterResponse(
    UUID studentId,
    String studentName,
    String className,
    String sectionName,
    long outstandingPaise,
    LocalDate oldestDueDate,
    long invoiceCount,
    int daysOverdue
) {}
