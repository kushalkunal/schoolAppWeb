package in.schoolapp.ocrservice.ocr;

import in.schoolapp.ocrservice.ocr.dto.OcrResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev default — returns deterministic mock OCR text so the full pipeline (LLM extraction +
 * downstream consumers) can be exercised without a Google Cloud account.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "LOGGING", matchIfMissing = true)
public class LoggingOcrProvider implements OcrProvider {

    private static final String MOCK_RECEIPT_TEXT = """
        Sunshine Public School
        Fee Receipt Book
        ---
        Receipt 1845  12 Apr 2025  Rohan Sharma 7B   Term 2 Fees   Rs. 4500
        Receipt 1846  12 Apr 2025  Priya Patel 5A    Term 2 Fees   Rs. 3200
        Receipt 1847  12 Apr 2025  Ankit S. 7B       Term 2 Fees   Rs. 2000
        Receipt 1848  13 Apr 2025  Aditya Kumar 5A   Term 2 Fees   Rs. 4500
        Receipt 1850  13 Apr 2025  Sneha Rao 4A      Term 2 Fees   Rs. 3800
        """;

    @Override
    public OcrResult extract(byte[] imageBytes) {
        log.info("[OCR-MOCK] returning canned receipt text bytes={} (set app.ocr.provider=GOOGLE_CLOUD_VISION for real)",
            imageBytes == null ? 0 : imageBytes.length);
        return new OcrResult(MOCK_RECEIPT_TEXT, 0.95);
    }
}
