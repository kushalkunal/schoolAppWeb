package in.schoolapp.migration;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.migration.dto.ExtractedRecord;
import in.schoolapp.migration.entity.MigrationJobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * HTTP client to the {@code ocr-service} microservice (slice 5). Replaces the in-process
 * OcrProvider + LlmExtractionProvider beans the backend used to host. The OCR service is
 * stateless and stateful retries are not yet wired — a failed call surfaces as
 * {@code EXTERNAL_SERVICE_ERROR} so the migration job goes to FAILED and the operator can
 * retry the upload.
 *
 * <p>Configuration: {@code app.migration.service.base-url}
 * (default {@code http://localhost:8090} for the dev compose setup).
 */
@Slf4j
@Component
public class OcrServiceClient {

    private final RestClient client;

    public OcrServiceClient(RestClient.Builder builder,
                            @Value("${app.migration.service.base-url:http://localhost:8090}") String baseUrl) {
        this.client = builder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
        log.info("OcrServiceClient configured baseUrl={}", baseUrl);
    }

    /**
     * Send an image to the OCR service and get structured records back.
     *
     * @return wire response carrying records, OCR raw text, confidence, and count
     */
    public OcrServiceResponse extract(MigrationJobType jobType, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "OCR image is empty");
        }
        Map<String, Object> body = Map.of(
            "jobType", jobType.name(),
            "imageBase64", Base64.getEncoder().encodeToString(imageBytes)
        );
        try {
            OcrServiceResponse out = client.post()
                .uri("/extract")
                .body(body)
                .retrieve()
                .body(OcrServiceResponse.class);
            if (out == null) {
                throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "OCR service returned an empty body");
            }
            log.info("OCR-service extracted records={} ocrChars={} confidence={}",
                out.recordCount(), out.rawText() == null ? 0 : out.rawText().length(),
                out.ocrConfidence());
            return out;
        } catch (RestClientResponseException e) {
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "OCR service returned " + e.getStatusCode() + ": " + e.getStatusText());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                "OCR service call failed: " + e.getMessage(), e);
        }
    }

    /** Wire shape — mirrors {@code in.schoolapp.ocrservice.dto.ExtractResponse}. */
    public record OcrServiceResponse(
        List<ExtractedRecord> records,
        String rawText,
        double ocrConfidence,
        int recordCount
    ) {}
}
