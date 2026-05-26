package in.schoolapp.communication;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.communication.dto.CircularResponse;
import in.schoolapp.communication.dto.CreateCircularRequest;
import in.schoolapp.communication.entity.Circular;
import in.schoolapp.communication.entity.CircularTargetType;
import in.schoolapp.communication.repository.CircularRepository;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.student.FamilyService;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.Parent;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.entity.StudentParentLink;
import in.schoolapp.student.repository.ParentRepository;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentParentLinkRepository;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bulk-broadcast circulars to parents (LLD §5.4). Create-and-send in a single call; the heavy
 * fan-out runs async on {@code notificationExecutor} so the HTTP response returns fast. The
 * {@link Circular} row is persisted synchronously so the admin sees it in the history list
 * immediately, and the counts tick up as the async dispatch progresses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircularService {

    private final CircularRepository circularRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final StudentParentLinkRepository linkRepository;
    private final ParentRepository parentRepository;
    private final SectionRepository sectionRepository;
    private final FamilyService familyService;
    private final WhatsAppNotifier whatsAppNotifier;
    private final AuditLogger auditLogger;

    @Transactional
    public CircularResponse createAndDispatch(UUID tenantId, CreateCircularRequest req) {
        validateTargets(req);
        Circular c = new Circular();
        c.setSchoolId(tenantId);
        c.setTitle(req.title().trim());
        c.setBody(req.body().trim());
        c.setTargetType(req.targetType());
        c.setTargetIds(req.targetIds() == null ? null : req.targetIds().toArray(UUID[]::new));
        c.setLanguage(req.language() == null || req.language().isBlank() ? "en" : req.language().trim());
        c.setCreatedById(TenantContext.getStaffId());
        c = circularRepository.save(c);
        log.info("Created circular id={} tenant={} target={}", c.getId(), tenantId, c.getTargetType());
        auditLogger.logCreate(tenantId, "Circular", c.getId(), Map.of(
            "title", c.getTitle(),
            "targetType", c.getTargetType().name(),
            "targetCount", req.targetIds() == null ? 0 : req.targetIds().size()
        ));
        dispatchAsync(c.getId(), tenantId);
        return CircularResponse.from(c);
    }

    @Transactional(readOnly = true)
    public Page<CircularResponse> list(UUID tenantId, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        Pageable p = PageRequest.of(Math.max(0, page), pageSize);
        return circularRepository.findBySchoolIdOrderByCreatedAtDesc(tenantId, p)
            .map(CircularResponse::from);
    }

    @Transactional(readOnly = true)
    public CircularResponse get(UUID tenantId, UUID id) {
        return CircularResponse.from(
            circularRepository.findByIdAndSchoolId(id, tenantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Circular", id)));
    }

    /**
     * Per-circular async dispatch. Runs after the outer @Transactional commits so the caller's
     * response is already on the wire when fan-out starts.
     */
    @Async("notificationExecutor")
    public void dispatchAsync(UUID circularId, UUID tenantId) {
        Circular c = circularRepository.findByIdAndSchoolId(circularId, tenantId).orElse(null);
        if (c == null) return;
        try {
            Set<UUID> parentIds = resolveRecipients(tenantId, c);
            int sent = 0, failed = 0;
            List<Parent> parents = parentRepository.findAllById(parentIds);
            for (Parent p : parents) {
                if (p.getPhone() == null || p.getPhone().isBlank()) { failed++; continue; }
                String body = formatBody(c, p);
                try {
                    whatsAppNotifier.send(WhatsAppMessage.text(
                        p.getPhone(), body, MessageType.CIRCULAR,
                        WhatsAppMessage.Audit.forParent(tenantId, null, p.getId(), p.getName())));
                    sent++;
                } catch (Exception e) {
                    log.warn("Circular dispatch failed parent={} — {}", p.getId(), e.getMessage());
                    failed++;
                }
            }
            updateCounts(circularId, sent, failed);
            log.info("Circular dispatched id={} tenant={} sent={} failed={}",
                circularId, tenantId, sent, failed);
        } catch (Exception e) {
            log.error("Circular dispatch errored id={} — {}", circularId, e.getMessage(), e);
        }
    }

    @Transactional
    void updateCounts(UUID circularId, int sent, int failed) {
        circularRepository.findById(circularId).ifPresent(c -> {
            c.setSentCount(c.getSentCount() + sent);
            c.setFailedCount(c.getFailedCount() + failed);
            if (c.getSentAt() == null) c.setSentAt(OffsetDateTime.now());
            circularRepository.save(c);
        });
    }

    // ------------------------------------------------------------
    // Recipient resolution
    // ------------------------------------------------------------

    private Set<UUID> resolveRecipients(UUID tenantId, Circular c) {
        CircularTargetType type = c.getTargetType();
        UUID[] targets = c.getTargetIds();
        return switch (type) {
            case ALL_PARENTS -> primaryParentsForAllStudents(tenantId);
            case CLASSES -> primaryParentsForClasses(tenantId, asList(targets));
            case SECTIONS -> primaryParentsForSections(asList(targets));
            case STUDENTS -> primaryParentsForStudents(asList(targets));
        };
    }

    private Set<UUID> primaryParentsForAllStudents(UUID tenantId) {
        Set<UUID> out = new HashSet<>();
        int page = 0, size = 200;
        while (true) {
            Page<in.schoolapp.student.entity.Student> p = studentRepository
                .findBySchoolIdAndActiveTrue(tenantId, PageRequest.of(page, size));
            if (p.isEmpty()) break;
            for (var s : p.getContent()) {
                UUID pid = familyService.getPrimaryParentId(s.getId());
                if (pid != null) out.add(pid);
            }
            if (p.isLast()) break;
            page++;
        }
        return out;
    }

    private Set<UUID> primaryParentsForSections(List<UUID> sectionIds) {
        Set<UUID> out = new HashSet<>();
        for (UUID sectionId : sectionIds) {
            for (StudentEnrollment e : enrollmentRepository
                    .findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE)) {
                UUID pid = familyService.getPrimaryParentId(e.getStudentId());
                if (pid != null) out.add(pid);
            }
        }
        return out;
    }

    private Set<UUID> primaryParentsForClasses(UUID tenantId, List<UUID> classIds) {
        // Collect all sections for these classes in the current year, then delegate.
        Set<UUID> sectionIds = new HashSet<>();
        for (Section sec : sectionRepository.findAll()) {
            if (!sec.getSchoolId().equals(tenantId)) continue;
            if (classIds.contains(sec.getClassId())) sectionIds.add(sec.getId());
        }
        return primaryParentsForSections(new ArrayList<>(sectionIds));
    }

    private Set<UUID> primaryParentsForStudents(List<UUID> studentIds) {
        Set<UUID> out = new HashSet<>();
        for (UUID sid : studentIds) {
            linkRepository.findByStudentIdAndPrimaryTrue(sid)
                .map(StudentParentLink::getParentId)
                .ifPresent(out::add);
        }
        return out;
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static void validateTargets(CreateCircularRequest req) {
        if (req.targetType() != CircularTargetType.ALL_PARENTS) {
            if (req.targetIds() == null || req.targetIds().isEmpty()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "targetIds must not be empty for targetType=" + req.targetType());
            }
        }
    }

    private static List<UUID> asList(UUID[] arr) {
        return arr == null ? List.of() : List.of(arr);
    }

    private static String formatBody(Circular c, Parent p) {
        String salutation = p.getName() == null || p.getName().isBlank()
            ? "Dear Parent" : "Dear " + p.getName();
        return salutation + ",\n\n" + c.getTitle() + "\n\n" + c.getBody();
    }
}
