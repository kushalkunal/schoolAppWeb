package in.schoolapp.migration.dto;

// ExtractedRecord lives in this same package since slice 5 (was migration.llm.dto previously,
// before the OCR/LLM packages were extracted into the ocr-service microservice).
import java.util.UUID;

/**
 * One LLM-extracted record enriched with entity-matching context, ready for the human review
 * grid. {@code matchedStudentId} is non-null when the match is unambiguous; otherwise the UI
 * shows {@code candidates} as a dropdown for manual disambiguation.
 */
public record ReviewableRecord(
    int rowIndex,                    // ordinal in the LLM output, used as a stable client ref
    ExtractedRecord extracted,
    UUID matchedStudentId,
    double matchConfidence,
    java.util.List<MatchCandidate> candidates
) {}
