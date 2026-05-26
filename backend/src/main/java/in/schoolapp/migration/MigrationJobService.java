package in.schoolapp.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.academics.MarksService;
import in.schoolapp.attendance.AttendanceService;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.fee.FeePaymentService;
import in.schoolapp.migration.dto.CommitMigrationRequest;
import in.schoolapp.migration.dto.MigrationJobResponse;
import in.schoolapp.migration.dto.ReviewableRecord;
import in.schoolapp.migration.entity.MigrationJob;
import in.schoolapp.migration.entity.MigrationJobStatus;
import in.schoolapp.migration.entity.MigrationJobType;
import in.schoolapp.migration.dto.ExtractedRecord;
import in.schoolapp.migration.event.MigrationJobUploadedEvent;
import in.schoolapp.migration.repository.MigrationJobRepository;
import in.schoolapp.storage.FileStorageService;
import in.schoolapp.storage.dto.StoredFile;
import in.schoolapp.student.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Migration job lifecycle:
 * <ol>
 *   <li>{@link #upload}        — persist image via FileStorageService, create UPLOADED job,
 *                                 fire {@link MigrationJobUploadedEvent}</li>
 *   <li>{@code MigrationProcessor} listens async — runs OCR + LLM, transitions to REVIEW</li>
 *   <li>{@link #getJob}        — UI polls for REVIEW status + reviewable rows</li>
 *   <li>{@link #commit}        — human-confirmed rows persisted as historical records</li>
 * </ol>
 * The OCR/LLM heavy work is deliberately async — uploads return immediately so a teacher
 * uploading 50 register pages doesn't block the UI thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationJobService {

    private final MigrationJobRepository jobRepository;
    private final FileStorageService fileStorage;
    private final EntityMatchingService entityMatching;
    private final FeePaymentService feePaymentService;
    private final AttendanceService attendanceService;
    private final MarksService marksService;
    private final StudentService studentService;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    @Transactional
    public MigrationJobResponse upload(UUID tenantId, MigrationJobType type, byte[] imageBytes,
                                       String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Migration image is empty");
        }

        // Persist image first — if storage fails, never create the job row
        UUID jobId = UUID.randomUUID();
        String key = "migration/" + tenantId + "/" + jobId + ".bin";
        StoredFile stored = fileStorage.store(key, imageBytes,
            contentType != null ? contentType : "application/octet-stream");

        MigrationJob job = new MigrationJob();
        job.setId(jobId);
        job.setSchoolId(tenantId);
        job.setJobType(type);
        job.setStatus(MigrationJobStatus.UPLOADED);
        job.setImageUrl(stored.url());
        job.setCreatedById(TenantContext.getStaffId());
        job = jobRepository.save(job);

        log.info("Created migration job id={} tenantId={} type={} bytes={}",
            jobId, tenantId, type, imageBytes.length);

        // Async pipeline picks it up from here
        events.publishEvent(new MigrationJobUploadedEvent(tenantId, jobId));

        return MigrationJobResponse.summary(job);
    }

    @Transactional(readOnly = true)
    public List<MigrationJobResponse> listJobs(UUID tenantId) {
        return jobRepository.findBySchoolIdOrderByCreatedAtDesc(tenantId).stream()
            .map(MigrationJobResponse::summary)
            .toList();
    }

    @Transactional(readOnly = true)
    public MigrationJobResponse getJob(UUID tenantId, UUID jobId) {
        MigrationJob job = getJobOrThrow(tenantId, jobId);

        List<ReviewableRecord> reviewable = job.getStatus() == MigrationJobStatus.REVIEW
            ? buildReviewableRecords(tenantId, job)
            : List.of();

        return MigrationJobResponse.from(job, reviewable);
    }

    /**
     * Commits the human-confirmed rows. Dispatches by {@link MigrationJobType}:
     * <ul>
     *   <li>FEE_RECEIPT     → {@link FeePaymentService#createHistorical}</li>
     *   <li>ATTENDANCE      → {@link AttendanceService#createHistorical}</li>
     *   <li>MARKS           → {@link MarksService#createHistorical} (one row = one subject mark)</li>
     *   <li>ADMISSION_FORM  → {@link StudentService#createHistoricalStudent}</li>
     * </ul>
     * Historical records skip downstream listeners (WhatsApp receipt, absence alert, etc.) —
     * parents already have the original paper artefact; we're only digitising the audit trail.
     */
    @Transactional
    public MigrationJobResponse commit(UUID tenantId, UUID jobId, CommitMigrationRequest req) {
        MigrationJob job = getJobOrThrow(tenantId, jobId);
        if (job.getStatus() != MigrationJobStatus.REVIEW) {
            throw new AppException(ErrorCode.MIGRATION_JOB_IN_WRONG_STATE,
                "Job must be in REVIEW state to commit; current=" + job.getStatus());
        }

        int committed = 0;
        for (CommitMigrationRequest.ConfirmedRow row : req.rows()) {
            commitRow(tenantId, job.getJobType(), row);
            committed++;
        }

        // Persist a snapshot of what was committed for auditability
        try {
            job.setReviewedJson(objectMapper.convertValue(req.rows(), Object.class));
        } catch (Exception ignore) {
            // Non-fatal: commit succeeded; only the audit JSON failed to serialise
        }
        job.setStatus(MigrationJobStatus.COMMITTED);
        job.setMatchedCount(committed);
        job.setCompletedAt(OffsetDateTime.now());
        jobRepository.save(job);

        log.info("Committed {} historical records jobId={} tenantId={}", committed, jobId, tenantId);
        return MigrationJobResponse.summary(job);
    }

    // -------- per-type commit dispatch --------

    private void commitRow(UUID tenantId, MigrationJobType type,
                           CommitMigrationRequest.ConfirmedRow row) {
        switch (type) {
            case FEE_RECEIPT -> commitFeeReceipt(tenantId, row);
            case ATTENDANCE  -> commitAttendance(tenantId, row);
            case MARKS       -> commitMarks(tenantId, row);
            case ADMISSION_FORM -> commitAdmissionForm(tenantId, row);
        }
    }

    private void commitFeeReceipt(UUID tenantId, CommitMigrationRequest.ConfirmedRow row) {
        requireField(row.studentId(),   "studentId",   "FEE_RECEIPT");
        requireField(row.amountPaise(), "amountPaise", "FEE_RECEIPT");
        requireField(row.paymentMode(), "paymentMode", "FEE_RECEIPT");
        feePaymentService.createHistorical(
            tenantId, row.studentId(), row.amountPaise(),
            row.paymentMode(), row.paymentDate(),
            row.externalReceiptNumber(), row.notes()
        );
    }

    private void commitAttendance(UUID tenantId, CommitMigrationRequest.ConfirmedRow row) {
        requireField(row.studentId(),        "studentId",        "ATTENDANCE");
        requireField(row.sectionId(),        "sectionId",        "ATTENDANCE");
        requireField(row.attendanceDate(),   "attendanceDate",   "ATTENDANCE");
        requireField(row.attendanceStatus(), "attendanceStatus", "ATTENDANCE");
        attendanceService.createHistorical(
            tenantId, row.studentId(), row.sectionId(),
            row.attendanceDate(), row.attendanceStatus(), row.notes()
        );
    }

    private void commitMarks(UUID tenantId, CommitMigrationRequest.ConfirmedRow row) {
        requireField(row.studentId(), "studentId", "MARKS");
        requireField(row.examId(),    "examId",    "MARKS");
        requireField(row.subjectId(), "subjectId", "MARKS");
        requireField(row.sectionId(), "sectionId", "MARKS");
        requireField(row.maxMarks(),  "maxMarks",  "MARKS");
        boolean absent = row.absent() != null && row.absent();
        marksService.createHistorical(
            tenantId, row.examId(), row.sectionId(),
            row.studentId(), row.subjectId(),
            row.maxMarks(), row.obtainedMarks(), absent
        );
    }

    private void commitAdmissionForm(UUID tenantId, CommitMigrationRequest.ConfirmedRow row) {
        requireField(row.firstName(),   "firstName",   "ADMISSION_FORM");
        requireField(row.sectionId(),   "sectionId",   "ADMISSION_FORM");
        requireField(row.parentPhone(), "parentPhone", "ADMISSION_FORM");
        studentService.createHistoricalStudent(
            tenantId, row.firstName(), row.lastName(), row.sectionId(),
            row.parentPhone(), row.parentName(), row.dateOfBirth(),
            row.gender(), row.notes()
        );
    }

    private static void requireField(Object value, String name, String type) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                type + " commit requires field '" + name + "'");
        }
    }

    // -------- review helpers (also called by MigrationProcessor) --------

    private List<ReviewableRecord> buildReviewableRecords(UUID tenantId, MigrationJob job) {
        if (job.getExtractedJson() == null) return List.of();
        List<ExtractedRecord> extracted;
        try {
            extracted = objectMapper.convertValue(
                job.getExtractedJson(),
                new TypeReference<List<ExtractedRecord>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialise extractedJson jobId={}: {}", job.getId(), e.getMessage());
            return List.of();
        }

        List<ReviewableRecord> out = new java.util.ArrayList<>(extracted.size());
        for (int i = 0; i < extracted.size(); i++) {
            ExtractedRecord rec = extracted.get(i);
            var match = entityMatching.match(tenantId, rec.studentName(), rec.classHint());
            out.add(new ReviewableRecord(i, rec, match.matchedStudentId(),
                match.topConfidence(), match.candidates()));
        }
        return out;
    }

    public MigrationJob getJobOrThrow(UUID tenantId, UUID jobId) {
        return jobRepository.findByIdAndSchoolId(jobId, tenantId)
            .orElseThrow(() -> AppException.notFound(
                ErrorCode.MIGRATION_JOB_NOT_FOUND, "MigrationJob", jobId));
    }
}
