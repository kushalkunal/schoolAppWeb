package in.schoolapp.fee.structure;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.fee.structure.dto.CreateVersionRequest;
import in.schoolapp.fee.structure.dto.FeeStructureVersionResponse;
import in.schoolapp.fee.structure.dto.MatrixResponse;
import in.schoolapp.fee.structure.dto.MatrixRowDto;
import in.schoolapp.fee.structure.dto.TermDto;
import in.schoolapp.fee.structure.dto.UpdateMatrixRequest;
import in.schoolapp.fee.structure.entity.FeeStructureRow;
import in.schoolapp.fee.structure.entity.FeeStructureTerm;
import in.schoolapp.fee.structure.entity.FeeStructureVersion;
import in.schoolapp.fee.structure.entity.FeeStructureVersion.Status;
import in.schoolapp.fee.structure.repository.FeeStructureRowRepository;
import in.schoolapp.fee.structure.repository.FeeStructureTermRepository;
import in.schoolapp.fee.structure.repository.FeeStructureVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the lifecycle of fee-structure versions and their matrix payload.
 * <p>
 * A version moves through DRAFT → ACTIVE → ARCHIVED. While DRAFT it is freely editable; once
 * ACTIVE the rows are immutable (callers must clone into a new DRAFT to make a change). At most
 * one ACTIVE version per (school, academic year) is allowed; activating a new one auto-archives
 * the previously active version.
 */
@Service
@RequiredArgsConstructor
public class FeeStructureService {

    private final FeeStructureVersionRepository versionRepo;
    private final FeeStructureTermRepository termRepo;
    private final FeeStructureRowRepository rowRepo;
    private final AuditLogger auditLogger;

    // ---------- Versions ----------

    @Transactional
    public FeeStructureVersionResponse create(UUID tenantId, CreateVersionRequest req, UUID userId) {
        FeeStructureVersion v = new FeeStructureVersion();
        v.setSchoolId(tenantId);
        v.setAcademicYearId(req.academicYearId());
        v.setName(req.name().trim());
        v.setNotes(req.notes());
        v.setStatus(Status.DRAFT);
        v.setCreatedById(userId);
        v = versionRepo.save(v);
        auditLogger.logCreate(tenantId, "FeeStructureVersion", v.getId(),
            Map.of("name", v.getName(), "academicYearId", req.academicYearId()));
        return FeeStructureVersionResponse.from(v);
    }

    @Transactional(readOnly = true)
    public List<FeeStructureVersionResponse> list(UUID tenantId) {
        return versionRepo.findBySchoolIdOrderByCreatedAtDesc(tenantId).stream()
            .map(FeeStructureVersionResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public MatrixResponse getMatrix(UUID tenantId, UUID versionId) {
        FeeStructureVersion v = require(tenantId, versionId);
        List<TermDto> terms = termRepo.findByVersionIdOrderByTermNumberAsc(versionId)
            .stream().map(TermDto::from).toList();
        List<MatrixRowDto> rows = rowRepo.findByVersionId(versionId)
            .stream().map(MatrixRowDto::from).toList();
        return new MatrixResponse(FeeStructureVersionResponse.from(v), terms, rows);
    }

    // ---------- Matrix mutation (DRAFT only) ----------

    @Transactional
    public MatrixResponse replaceMatrix(UUID tenantId, UUID versionId, UpdateMatrixRequest req) {
        FeeStructureVersion v = require(tenantId, versionId);
        requireDraft(v);

        // Validate: term numbers referenced by rows must exist in the submitted terms (unless null).
        Set<Integer> termNumbers = new HashSet<>();
        for (TermDto t : req.terms()) {
            if (!termNumbers.add(t.termNumber())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Duplicate term number: " + t.termNumber());
            }
            if (t.endDate().isBefore(t.startDate())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Term '" + t.name() + "' end date is before start date");
            }
        }
        for (MatrixRowDto r : req.rows()) {
            if (r.termNumber() != null && !termNumbers.contains(r.termNumber())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Row references unknown term number " + r.termNumber());
            }
        }
        // Catch duplicate (class, head, term) cells defensively — DB unique catches it too but
        // the message would be a Hibernate exception, not our ApiResponse.
        Set<String> seen = new HashSet<>();
        for (MatrixRowDto r : req.rows()) {
            String key = r.classId() + ":" + r.feeHeadId() + ":" + r.termNumber();
            if (!seen.add(key)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Duplicate matrix row for class+head+term combination");
            }
        }

        termRepo.deleteByVersionId(versionId);
        rowRepo.deleteByVersionId(versionId);
        // flush implicitly via JPA on save below; explicit flush not required here because we
        // immediately persist new rows that won't collide with the deleted ones (different IDs).

        for (TermDto t : req.terms()) {
            FeeStructureTerm e = new FeeStructureTerm();
            e.setVersionId(versionId);
            e.setTermNumber(t.termNumber());
            e.setName(t.name());
            e.setStartDate(t.startDate());
            e.setEndDate(t.endDate());
            e.setDueDate(t.dueDate());
            termRepo.save(e);
        }
        for (MatrixRowDto r : req.rows()) {
            FeeStructureRow e = new FeeStructureRow();
            e.setVersionId(versionId);
            e.setClassId(r.classId());
            e.setFeeHeadId(r.feeHeadId());
            e.setTermNumber(r.termNumber());
            e.setAmountPaise(r.amountPaise());
            e.setOptional(r.optional());
            rowRepo.save(e);
        }

        auditLogger.logUpdate(tenantId, "FeeStructureVersion", versionId,
            Map.of(), Map.of("termCount", req.terms().size(), "rowCount", req.rows().size()));
        return getMatrix(tenantId, versionId);
    }

    /**
     * Replace just the matrix rows of a DRAFT version, leaving terms untouched. Used by the CSV
     * importer — the CSV format describes (class, head, term, amount) cells but not term dates,
     * so we preserve whatever the editor previously configured for terms.
     */
    @Transactional
    public MatrixResponse replaceRows(UUID tenantId, UUID versionId, List<MatrixRowDto> rows) {
        FeeStructureVersion v = require(tenantId, versionId);
        requireDraft(v);

        Set<Integer> termNumbers = new HashSet<>();
        for (FeeStructureTerm t : termRepo.findByVersionIdOrderByTermNumberAsc(versionId)) {
            termNumbers.add(t.getTermNumber());
        }
        for (MatrixRowDto r : rows) {
            if (r.termNumber() != null && !termNumbers.contains(r.termNumber())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "CSV row references undefined term " + r.termNumber()
                        + " — define the term in the editor first");
            }
        }
        Set<String> seen = new HashSet<>();
        for (MatrixRowDto r : rows) {
            String key = r.classId() + ":" + r.feeHeadId() + ":" + r.termNumber();
            if (!seen.add(key)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Duplicate (class + fee head + term) in CSV");
            }
        }

        rowRepo.deleteByVersionId(versionId);
        for (MatrixRowDto r : rows) {
            FeeStructureRow e = new FeeStructureRow();
            e.setVersionId(versionId);
            e.setClassId(r.classId());
            e.setFeeHeadId(r.feeHeadId());
            e.setTermNumber(r.termNumber());
            e.setAmountPaise(r.amountPaise());
            e.setOptional(r.optional());
            rowRepo.save(e);
        }

        auditLogger.logUpdate(tenantId, "FeeStructureVersion", versionId,
            Map.of(), Map.of("source", "CSV_IMPORT", "rowCount", rows.size()));
        return getMatrix(tenantId, versionId);
    }

    // ---------- Status transitions ----------

    @Transactional
    public FeeStructureVersionResponse activate(UUID tenantId, UUID versionId) {
        FeeStructureVersion v = require(tenantId, versionId);
        if (v.getStatus() == Status.ARCHIVED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Cannot activate an ARCHIVED version. Clone into a new draft.");
        }
        if (v.getStatus() == Status.ACTIVE) {
            return FeeStructureVersionResponse.from(v); // idempotent
        }
        // Auto-archive existing active version for the same (school, academic year).
        versionRepo.findFirstBySchoolIdAndAcademicYearIdAndStatus(
                tenantId, v.getAcademicYearId(), Status.ACTIVE)
            .ifPresent(prev -> {
                prev.setStatus(Status.ARCHIVED);
                prev.setArchivedAt(OffsetDateTime.now());
                versionRepo.save(prev);
                auditLogger.logUpdate(tenantId, "FeeStructureVersion", prev.getId(),
                    Map.of("status", "ACTIVE"), Map.of("status", "ARCHIVED"));
            });
        v.setStatus(Status.ACTIVE);
        v.setActivatedAt(OffsetDateTime.now());
        versionRepo.save(v);
        auditLogger.logUpdate(tenantId, "FeeStructureVersion", versionId,
            Map.of("status", "DRAFT"), Map.of("status", "ACTIVE"));
        return FeeStructureVersionResponse.from(v);
    }

    @Transactional
    public FeeStructureVersionResponse archive(UUID tenantId, UUID versionId) {
        FeeStructureVersion v = require(tenantId, versionId);
        if (v.getStatus() == Status.ARCHIVED) return FeeStructureVersionResponse.from(v);
        String prev = v.getStatus().name();
        v.setStatus(Status.ARCHIVED);
        v.setArchivedAt(OffsetDateTime.now());
        versionRepo.save(v);
        auditLogger.logUpdate(tenantId, "FeeStructureVersion", versionId,
            Map.of("status", prev), Map.of("status", "ARCHIVED"));
        return FeeStructureVersionResponse.from(v);
    }

    // ---------- Lookups for the generator ----------

    @Transactional(readOnly = true)
    public FeeStructureVersion requireActive(UUID tenantId, UUID versionId) {
        FeeStructureVersion v = require(tenantId, versionId);
        if (v.getStatus() != Status.ACTIVE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Cannot generate invoices from a " + v.getStatus() + " version");
        }
        return v;
    }

    public FeeStructureVersion require(UUID tenantId, UUID versionId) {
        return versionRepo.findByIdAndSchoolId(versionId, tenantId)
            .orElseThrow(() -> AppException.notFound(
                ErrorCode.RESOURCE_NOT_FOUND, "FeeStructureVersion", versionId));
    }

    private void requireDraft(FeeStructureVersion v) {
        if (v.getStatus() != Status.DRAFT) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Matrix can only be edited while DRAFT. Current status: " + v.getStatus());
        }
    }
}
