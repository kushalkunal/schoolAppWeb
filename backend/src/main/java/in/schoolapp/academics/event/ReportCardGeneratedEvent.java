package in.schoolapp.academics.event;

import java.util.UUID;

/** Fired per-student after a report card PDF is generated and persisted. */
public record ReportCardGeneratedEvent(
    UUID tenantId,
    UUID reportCardId,
    UUID studentId,
    UUID examId,
    String examName,
    String pdfUrl
) {}
