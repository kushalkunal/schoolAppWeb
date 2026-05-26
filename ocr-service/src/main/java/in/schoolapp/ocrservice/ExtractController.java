package in.schoolapp.ocrservice;

import in.schoolapp.ocrservice.dto.ExtractRequest;
import in.schoolapp.ocrservice.dto.ExtractResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ExtractController {

    private final ExtractionService extractionService;

    /**
     * Stateless extraction. Main backend posts the image (base64) + jobType; we run OCR
     * (Google Vision) and LLM extraction (OpenAI/Anthropic/Gemini) and return the structured
     * records. No DB, no state — the backend persists results into its own MigrationJob row.
     */
    @PostMapping("/extract")
    public ExtractResponse extract(@Valid @RequestBody ExtractRequest req) {
        try {
            return extractionService.extract(req.jobType(), req.imageBase64());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @ExceptionHandler(RuntimeException.class)
    public org.springframework.http.ResponseEntity<Map<String, Object>> onError(RuntimeException e) {
        return org.springframework.http.ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("error", "EXTRACTION_FAILED", "message", e.getMessage() == null ? "" : e.getMessage()));
    }
}
