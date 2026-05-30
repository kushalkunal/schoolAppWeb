package in.schoolapp.student.dto;

import in.schoolapp.student.entity.EnrollmentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record WithdrawalResponse(
    UUID studentId,
    EnrollmentStatus status,
    long outstandingPaise,
    boolean duesOverridden,
    LocalDate leavingDate
) {}
