package in.schoolapp.ocrservice.dto;

import java.util.List;

/**
 * Response from {@code POST /extract}. Carries the structured records plus the raw OCR text
 * (so the backend can store it on the MigrationJob row for audit / re-processing) and the
 * provider's confidence.
 */
public record ExtractResponse(
    List<ExtractedRecord> records,
    String rawText,
    double ocrConfidence,
    int recordCount
) {}
