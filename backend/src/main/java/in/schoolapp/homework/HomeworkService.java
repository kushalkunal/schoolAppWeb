package in.schoolapp.homework;

import in.schoolapp.academics.repository.TeacherSubjectAssignmentRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.parent.ParentNotificationService;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.homework.dto.AssignmentDto;
import in.schoolapp.homework.dto.SubmissionDto;
import in.schoolapp.homework.entity.HomeworkAssignment;
import in.schoolapp.homework.entity.HomeworkSubmission;
import in.schoolapp.homework.repository.HomeworkAssignmentRepository;
import in.schoolapp.homework.repository.HomeworkSubmissionRepository;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeworkService {

    private final HomeworkAssignmentRepository assignmentRepo;
    private final HomeworkSubmissionRepository submissionRepo;
    private final StudentEnrollmentRepository enrollmentRepo;
    private final ParentNotificationService parentNotificationService;
    private final ClassSectionService classSectionService;
    private final TeacherSubjectAssignmentRepository teacherAssignmentRepository;
    private final in.schoolapp.timetable.repository.TimetableEntryRepository timetableEntryRepository;
    private final in.schoolapp.timetable.repository.TimetableSubstitutionRepository substitutionRepository;
    private final in.schoolapp.school.repository.StaffRepository staffRepository;
    private final in.schoolapp.academics.repository.SubjectRepository subjectRepository;

    // ---------- Assignments ----------

    @Transactional
    public AssignmentDto createAssignment(UUID tenantId, AssignmentDto req) {
        // RBAC: a teacher may assign homework only to a section they actually teach — whether as a
        // regular teacher (class teacher of it, or a subject/timetable assignment there) or as a
        // substitute with an active substitution for it today. PRINCIPAL/ADMIN/SCHOOL_OWNER are
        // unrestricted. Every assignment records who created it + when (the homework history).
        String role = TenantContext.getRole();
        boolean isAdmin = "PRINCIPAL".equals(role) || "ADMIN".equals(role) || "SCHOOL_OWNER".equals(role);
        if (!isAdmin) {
            var section = classSectionService.getSectionOrThrow(tenantId, req.sectionId());
            if (!teachesSection(tenantId, TenantContext.getStaffId(), req.sectionId(), section)) {
                throw new AppException(ErrorCode.FORBIDDEN,
                    "You can only assign homework to a class you teach (or are substituting for today).");
            }
        }

        HomeworkAssignment a = new HomeworkAssignment();
        a.setSchoolId(tenantId);
        a.setSectionId(req.sectionId());
        a.setSubjectId(req.subjectId());
        a.setTitle(req.title());
        a.setBody(req.body());
        a.setAttachmentUrl(req.attachmentUrl());
        a.setDueDate(req.dueDate());
        HomeworkAssignment saved = assignmentRepo.save(a);
        notifyParentsOfHomework(tenantId, saved);
        return AssignmentDto.from(saved);
    }

    /** True when the teacher teaches the section: class teacher of it, a subject/timetable
     *  assignment there, or an active substitution for it today. */
    private boolean teachesSection(UUID tenantId, UUID staffId, UUID sectionId,
                                   in.schoolapp.school.entity.Section section) {
        if (staffId == null) return false;
        if (staffId.equals(section.getClassTeacherId())) return true;
        boolean assignedSubject = teacherAssignmentRepository
            .findByStaffIdAndAcademicYearId(staffId, section.getAcademicYearId()).stream()
            .anyMatch(a -> sectionId.equals(a.getSectionId()));
        if (assignedSubject) return true;
        boolean inTimetable = timetableEntryRepository.findByTeacherId(staffId).stream()
            .anyMatch(e -> sectionId.equals(e.getSectionId()) && tenantId.equals(e.getSchoolId()));
        if (inTimetable) return true;
        return substitutionRepository.findBySubstituteTeacherIdAndDate(staffId, java.time.LocalDate.now()).stream()
            .anyMatch(s -> sectionId.equals(s.getSectionId()) && tenantId.equals(s.getSchoolId()));
    }

    /**
     * Slice 33 — notify the parent of every ACTIVE student in the section that a new homework
     * assignment was posted. Async + gated by {@link FeatureKey#PARENT_NOTIFY_HOMEWORK} inside
     * {@link ParentNotificationService}. Always runs (no admin opt-in needed) — the flag is the
     * gate. Errors are swallowed by the dispatcher; we never let a notification crash a write.
     */
    @Async("notificationExecutor")
    public void notifyParentsOfHomework(UUID tenantId, HomeworkAssignment a) {
        String due = a.getDueDate() == null ? "—" : a.getDueDate().toString();
        String body = String.format(
            "📚 *New homework assigned: %s*\n\n%s\n\nDue: %s",
            a.getTitle(),
            a.getBody() == null ? "" : a.getBody(),
            due);
        String subject = "New homework: " + a.getTitle();
        List<StudentEnrollment> enrollments =
            enrollmentRepo.findBySectionIdAndStatus(a.getSectionId(), EnrollmentStatus.ACTIVE);
        for (StudentEnrollment enr : enrollments) {
            parentNotificationService.notify(
                tenantId, enr.getStudentId(),
                FeatureKey.PARENT_NOTIFY_HOMEWORK,
                subject, body, MessageType.CIRCULAR);
        }
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> listForSection(UUID tenantId, UUID sectionId) {
        return assignmentRepo.findBySectionIdOrderByDueDateDesc(sectionId).stream()
            .filter(a -> a.getSchoolId().equals(tenantId))
            .map(AssignmentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> listForTenant(UUID tenantId) {
        return assignmentRepo.findBySchoolIdOrderByCreatedAtDesc(tenantId).stream()
            .map(AssignmentDto::from).toList();
    }

    /** Admin homework log: who assigned which homework, to which class, on which day (history). */
    @Transactional(readOnly = true)
    public List<in.schoolapp.homework.dto.HomeworkLogRow> homeworkLog(
            UUID tenantId, java.time.LocalDate from, java.time.LocalDate to) {
        java.util.Map<UUID, String> staffName = staffRepository
            .findBySchoolIdAndActiveTrueOrderByFirstName(tenantId).stream()
            .collect(java.util.stream.Collectors.toMap(s -> s.getId(), s -> s.displayName(), (a, b) -> a));
        java.util.Map<UUID, String> subjectName = subjectRepository.findBySchoolIdOrderByName(tenantId).stream()
            .collect(java.util.stream.Collectors.toMap(s -> s.getId(), s -> s.getName(), (a, b) -> a));
        java.util.Map<UUID, String> sectionLabel = new java.util.HashMap<>();
        for (var c : classSectionService.listClasses(tenantId)) {
            for (var sec : c.sections()) sectionLabel.put(sec.id(), c.name() + " - " + sec.name());
        }
        return assignmentRepo.findBySchoolIdOrderByCreatedAtDesc(tenantId).stream()
            .filter(a -> {
                java.time.LocalDate d = a.getCreatedAt().toLocalDate();
                return !d.isBefore(from) && !d.isAfter(to);
            })
            .map(a -> new in.schoolapp.homework.dto.HomeworkLogRow(
                a.getCreatedAt().toLocalDate(),
                a.getCreatedByStaffId() != null ? staffName.getOrDefault(a.getCreatedByStaffId(), "—") : "—",
                sectionLabel.getOrDefault(a.getSectionId(), "—"),
                a.getSubjectId() != null ? subjectName.getOrDefault(a.getSubjectId(), "—") : "—",
                a.getTitle(),
                a.getDueDate()))
            .toList();
    }

    @Transactional
    public void deleteAssignment(UUID tenantId, UUID assignmentId) {
        HomeworkAssignment a = assignmentRepo.findById(assignmentId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Assignment", assignmentId));
        if (!a.getSchoolId().equals(tenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Assignment not in this school");
        }
        assignmentRepo.delete(a);
    }

    // ---------- Submissions ----------

    @Transactional
    public SubmissionDto submitOrUpdate(UUID tenantId, SubmissionDto req) {
        // Upsert: one submission per (assignment, student). Re-submission replaces.
        HomeworkSubmission s = submissionRepo
            .findByAssignmentIdAndStudentId(req.assignmentId(), req.studentId())
            .orElseGet(() -> {
                HomeworkSubmission ns = new HomeworkSubmission();
                ns.setSchoolId(tenantId);
                ns.setAssignmentId(req.assignmentId());
                ns.setStudentId(req.studentId());
                ns.setSubmittedAt(OffsetDateTime.now());
                return ns;
            });
        s.setSubmissionText(req.submissionText());
        s.setAttachmentUrl(req.attachmentUrl());
        return SubmissionDto.from(submissionRepo.save(s));
    }

    @Transactional
    public SubmissionDto gradeSubmission(UUID tenantId, UUID submissionId,
                                          String grade, String teacherRemark) {
        HomeworkSubmission s = submissionRepo.findById(submissionId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Submission", submissionId));
        if (!s.getSchoolId().equals(tenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Submission not in this school");
        }
        s.setGrade(grade);
        s.setTeacherRemark(teacherRemark);
        s.setGradedAt(OffsetDateTime.now());
        return SubmissionDto.from(submissionRepo.save(s));
    }

    @Transactional(readOnly = true)
    public List<SubmissionDto> listForAssignment(UUID tenantId, UUID assignmentId) {
        return submissionRepo.findByAssignmentId(assignmentId).stream()
            .filter(s -> s.getSchoolId().equals(tenantId))
            .map(SubmissionDto::from).toList();
    }
}
