package in.schoolapp.student.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Withdraw a student from the school. {@code overrideDues=true} lets an authorised user proceed
 * despite outstanding fees (the override + amount are audited).
 */
public record WithdrawRequest(
    @Size(max = 500) String reason,
    boolean overrideDues,
    LocalDate leavingDate
) {}
