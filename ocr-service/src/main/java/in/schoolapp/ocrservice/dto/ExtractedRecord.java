package in.schoolapp.ocrservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.Map;

/**
 * One row extracted by the LLM. Wire-compatible with {@code in.schoolapp.migration.dto.ExtractedRecord}
 * in the main backend — JSON field names match.
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
