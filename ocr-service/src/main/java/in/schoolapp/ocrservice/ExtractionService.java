package in.schoolapp.ocrservice;

import in.schoolapp.ocrservice.dto.ExtractResponse;
import in.schoolapp.ocrservice.dto.ExtractedRecord;
import in.schoolapp.ocrservice.llm.LlmExtractionProvider;
import in.schoolapp.ocrservice.ocr.OcrProvider;
import in.schoolapp.ocrservice.ocr.dto.OcrResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

/**
 * Stateless OCR → LLM pipeline. Single entry point used by the controller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private final OcrProvider ocrProvider;
    private final LlmExtractionProvider llmProvider;

    public ExtractResponse extract(JobType jobType, String imageBase64) {
        byte[] imageBytes = decodeImage(imageBase64);
        OcrResult ocr = ocrProvider.extract(imageBytes);
        log.info("OCR complete jobType={} chars={} confidence={}",
            jobType, ocr.text().length(), ocr.confidence());

        List<ExtractedRecord> records = llmProvider.extract(ocr.text(), jobType);
        log.info("LLM extraction complete jobType={} records={}", jobType, records.size());

        return new ExtractResponse(records, ocr.text(), ocr.confidence(), records.size());
    }

    private byte[] decodeImage(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("imageBase64 is empty");
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("imageBase64 is not valid base64: " + e.getMessage());
        }
    }
}
