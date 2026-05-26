package in.schoolapp.documents.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * All fields are optional — the service fills sensible defaults (today as leaving date,
 * "On parent's request" as reason, "Good" conduct, "None" fees due). The school clerk
 * overrides whichever ones matter for the specific student.
 */
public record IssueTransferCertificateRequest(
    LocalDate admissionDate,
    LocalDate leavingDate,
    @Size(max = 200) String reasonForLeaving,
    @Size(max = 50)  String conduct,
    boolean promoted,
    @Size(max = 100) String feesDue,
    @Size(max = 500) String remarks,
    @Size(max = 40)  String tcNumber
) {}
