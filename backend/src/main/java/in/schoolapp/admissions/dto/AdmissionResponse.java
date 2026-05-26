package in.schoolapp.admissions.dto;

import in.schoolapp.admissions.entity.Admission;
import in.schoolapp.admissions.entity.AdmissionStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdmissionResponse(
    UUID id,
    AdmissionStatus status,
    String parentName,
    String parentPhone,
    String parentEmail,
    String studentFirstName,
    String studentLastName,
    String studentDisplayName,
    LocalDate studentDateOfBirth,
    String studentGender,
    String intendedClass,
    String intendedSection,
    String intendedAcademicYear,
    String source,
    String referrerName,
    String notes,
    OffsetDateTime testScheduledAt,
    String testVenue,
    Integer testTotalMarks,
    Integer testObtainedMarks,
    String testRemarks,
    List<AdmissionTestScoreDto> testScores,
    String offerLetterUrl,
    OffsetDateTime offerIssuedAt,
    OffsetDateTime offerAcceptedAt,
    OffsetDateTime offerDeclinedAt,
    String declineReason,
    UUID enrolledStudentId,
    OffsetDateTime enrolledAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static AdmissionResponse from(Admission a, List<AdmissionTestScoreDto> scores) {
        return new AdmissionResponse(
            a.getId(), a.getStatus(),
            a.getParentName(), a.getParentPhone(), a.getParentEmail(),
            a.getStudentFirstName(), a.getStudentLastName(), a.studentDisplayName(),
            a.getStudentDateOfBirth(), a.getStudentGender(),
            a.getIntendedClass(), a.getIntendedSection(), a.getIntendedAcademicYear(),
            a.getSource(), a.getReferrerName(), a.getNotes(),
            a.getTestScheduledAt(), a.getTestVenue(),
            a.getTestTotalMarks(), a.getTestObtainedMarks(), a.getTestRemarks(),
            scores,
            a.getOfferLetterUrl(), a.getOfferIssuedAt(),
            a.getOfferAcceptedAt(), a.getOfferDeclinedAt(),
            a.getDeclineReason(),
            a.getEnrolledStudentId(), a.getEnrolledAt(),
            a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
