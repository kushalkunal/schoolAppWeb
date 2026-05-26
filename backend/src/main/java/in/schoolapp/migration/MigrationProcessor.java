package in.schoolapp.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.migration.entity.MigrationJob;
import in.schoolapp.migration.entity.MigrationJobStatus;
import in.schoolapp.migration.event.MigrationJobUploadedEvent;
import in.schoolapp.migration.repository.MigrationJobRepository;
import in.schoolapp.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLConnection;
import java.time.OffsetDateTime;

/**
 * Async worker for migration jobs.
 *
 * <p><strong>Slice 5:</strong> OCR + LLM extraction moved to the {@code ocr-service}
 * microservice. This processor now:
 * <ol>
 *   <li>Pulls the uploaded image bytes from storage.</li>
 *   <li>POSTs them to the OCR service via {@link OcrServiceClient}.</li>
 *   <li>Stores the returned structured records + raw OCR text on the job row.</li>
 * </ol>
 *
 * <p>Runs on the {@code ocrExecutor} pool defined in {@code AsyncConfig}. Failure is captured
 * on the job (status = FAILED, error_message populated); never thrown back, since the upload
 * HTTP response has already returned.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigrationProcessor {

    private final MigrationJobRepository jobRepository;
    private final OcrServiceClient ocrServiceClient;
    private final ObjectMapper objectMapper;
    @SuppressWarnings("unused")
    private final FileStorageService fileStorageService;

    @Async("ocrExecutor")
    @EventListener
    @Transactional
    public void process(MigrationJobUploadedEvent event) {
        MigrationJob job = jobRepository.findById(event.jobId()).orElse(null);
        if (job == null) {
            log.warn("MigrationJob {} disappeared before processing", event.jobId());
            return;
        }

        try {
            job.setStatus(MigrationJobStatus.PROCESSING);
            jobRepository.save(job);

            byte[] imageBytes = downloadImage(job.getImageUrl());
            OcrServiceClient.OcrServiceResponse out = ocrServiceClient.extract(
                job.getJobType(), imageBytes);

            job.setRawOcrText(out.rawText());
            job.setExtractedJson(objectMapper.convertValue(out.records(), Object.class));
            job.setRecordCount(out.recordCount());
            job.setStatus(MigrationJobStatus.REVIEW);
            jobRepository.save(job);
        } catch (Exception e) {
            log.error("Migration processing failed jobId={}", event.jobId(), e);
            job.setStatus(MigrationJobStatus.FAILED);
            job.setErrorMessage(truncate(e.getMessage()));
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);
        }
    }

    private byte[] downloadImage(String url) throws Exception {
        URLConnection conn = URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        try (var in = conn.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1000 ? s.substring(0, 1000) + "…" : s;
    }
}
