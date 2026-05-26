package in.schoolapp.ocrservice.llm;

import in.schoolapp.ocrservice.JobType;
import in.schoolapp.ocrservice.dto.ExtractedRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Dev default — deterministic mock records mirroring LoggingOcrProvider's canned receipt.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "LOGGING", matchIfMissing = true)
public class LoggingLlmProvider implements LlmExtractionProvider {

    @Override
    public List<ExtractedRecord> extract(String ocrText, JobType type) {
        log.info("[LLM-MOCK] type={} ocrChars={} returning mock records",
            type, ocrText == null ? 0 : ocrText.length());
        if (type == JobType.FEE_RECEIPT) {
            return List.of(
                fee("Rohan Sharma", "7B", 450000L, "1845", LocalDate.parse("2025-04-12"), 0.94),
                fee("Priya Patel",  "5A", 320000L, "1846", LocalDate.parse("2025-04-12"), 0.92),
                fee("Ankit S.",     "7B", 200000L, "1847", LocalDate.parse("2025-04-12"), 0.78),
                fee("Aditya Kumar", "5A", 450000L, "1848", LocalDate.parse("2025-04-13"), 0.93),
                fee("Sneha Rao",    "4A", 380000L, "1850", LocalDate.parse("2025-04-13"), 0.91)
            );
        }
        return List.of();
    }

    private static ExtractedRecord fee(String name, String classHint, long paise,
                                       String receipt, LocalDate date, double confidence) {
        return new ExtractedRecord(name, classHint, paise, receipt, date, "Term 2 Fees",
            confidence, Map.of("source", "LOGGING-MOCK"));
    }
}
