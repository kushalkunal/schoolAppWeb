package in.schoolapp.migration.dto;

import in.schoolapp.attendance.entity.AttendanceStatus;
import in.schoolapp.fee.entity.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Human-confirmed rows ready to commit. The reviewer has resolved any low-confidence matches
 * by selecting a {@code studentId} per row (or, for ADMISSION_FORM, supplying the new
 * student's details). Rows omitted from the request are dropped (UI default = "Confirm
 * green-only" skips orange/red rows).
 * <p>
 * Fields are deliberately nullable across types — the commit dispatcher in
 * {@link in.schoolapp.migration.MigrationJobService} validates per-type. Keeping one flat row
 * shape avoids Jackson polymorphism overhead; the per-type fields are documented in each
 * section below.
 */
public record CommitMigrationRequest(
    @NotEmpty @Valid List<ConfirmedRow> rows
) {
    public record ConfirmedRow(
        @NotNull Integer rowIndex,

        /** Required for FEE_RECEIPT, ATTENDANCE, MARKS. Null for ADMISSION_FORM (student
         *  doesn't exist yet — created during commit). */
        UUID studentId,

        // ---- FEE_RECEIPT ----
        Long amountPaise,
        PaymentMode paymentMode,
        LocalDate paymentDate,
        String externalReceiptNumber,

        // ---- ATTENDANCE ----
        UUID sectionId,              // shared with ADMISSION_FORM; indicates target section
        LocalDate attendanceDate,
        AttendanceStatus attendanceStatus,

        // ---- MARKS (one row = one subject mark) ----
        UUID examId,
        UUID subjectId,
        BigDecimal maxMarks,
        BigDecimal obtainedMarks,
        Boolean absent,

        // ---- ADMISSION_FORM (creating a new student) ----
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String gender,
        String parentName,
        String parentPhone,

        // Common — free-form reviewer notes
        String notes
    ) {}
}
