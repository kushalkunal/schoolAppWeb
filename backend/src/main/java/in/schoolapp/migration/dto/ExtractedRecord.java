package in.schoolapp.migration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.Map;

/**
 * One row extracted by the LLM from OCR text. Returned by the {@code ocr-service} microservice
 * via HTTP — wire-compatible JSON. Stays in the backend as the contract for the migration
 * commit pipeline (matched against students by EntityMatchingService and surfaced to the
 * review UI).
 *
 * <p>Most fields nullable because LLMs hallucinate less when allowed to skip rather than
 * forced to invent. {@code rawFields} preserves type-specific fields without needing a Java
 * schema per job type.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractedRecord(
    String studentName,
    String classHint,
    Long amountPaise,
    String receiptNumber,
    LocalDate date,
    String description,
    Double confidence,
    Map<String, Object> rawFields
) {}
