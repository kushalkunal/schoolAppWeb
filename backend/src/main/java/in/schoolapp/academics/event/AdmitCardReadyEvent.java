package in.schoolapp.academics.event;

import java.util.UUID;

/**
 * Published when a student's admit card has been generated. A communication listener notifies the
 * student's parent (WhatsApp / email / SMS) with the download link — the hall ticket itself carries
 * the exam schedule, so this also serves as the exam-schedule touchpoint for parents.
 */
public record AdmitCardReadyEvent(
    UUID tenantId,
    UUID studentId,
    String examName,
    String pdfUrl
) {}
