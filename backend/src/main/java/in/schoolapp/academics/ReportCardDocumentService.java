package in.schoolapp.academics;

import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamMark;
import in.schoolapp.academics.entity.ReportCard;
import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.repository.ExamMarkRepository;
import in.schoolapp.academics.repository.ReportCardRepository;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.attendance.entity.AttendanceRecord;
import in.schoolapp.attendance.entity.AttendanceStatus;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.documents.DocumentService;
import in.schoolapp.documents.DocumentService.GeneratedDocument;
import in.schoolapp.documents.DocumentType;
import in.schoolapp.school.dto.SchoolResponse;
import in.schoolapp.school.SchoolService;
import in.schoolapp.student.FamilyService;
import in.schoolapp.student.dto.StudentProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Single source of truth for rendering a report-card PDF, shared by the on-demand download
 * endpoint and the bulk {@code generateForSection} path so both produce the identical,
 * fully-branded document — Thymeleaf {@code report_card.html} via {@link DocumentService},
 * which carries the QR verification code, signature block, per-subject marks, attendance
 * summary and class-teacher remarks. Replaces the old programmatic iText generator that the
 * bulk path used (no QR / no signature / basic layout).
 *
 * <p>The model is assembled as plain {@link Map}s (resolved here in Java from the DTOs/entities)
 * so Thymeleaf's SpEL Map accessor renders it deterministically, independent of DTO accessor
 * shapes. Requires the {@link ReportCard} row to already exist (totals/grade/rank/remarks are
 * read from it) — true for both callers, which persist it first.
 */
@Service
@RequiredArgsConstructor
public class ReportCardDocumentService {

    private final ReportCardRepository reportCardRepository;
    private final ExamMarkRepository markRepository;
    private final SubjectRepository subjectRepository;
    private final AttendanceRepository attendanceRepository;
    private final SchoolService schoolService;
    private final ExamService examService;
    private final FamilyService familyService;
    private final DocumentService documentService;

    @Transactional(readOnly = true)
    public GeneratedDocument renderPdf(UUID tenantId, UUID studentId, UUID examId) {
        ReportCard rc = reportCardRepository.findByStudentIdAndExamId(studentId, examId)
            .orElseThrow(() -> new AppException(ErrorCode.REPORT_CARD_NOT_READY,
                "Report card has not been generated yet"));
        return documentService.generate(tenantId, DocumentType.REPORT_CARD,
            buildModel(tenantId, studentId, examId, rc));
    }

    /** Convenience for callers that only need the bytes (bulk store/WhatsApp path). */
    @Transactional(readOnly = true)
    public byte[] renderPdfBytes(UUID tenantId, UUID studentId, UUID examId) {
        return renderPdf(tenantId, studentId, examId).bytes();
    }

    private Map<String, Object> buildModel(UUID tenantId, UUID studentId, UUID examId, ReportCard rc) {
        SchoolResponse school = schoolService.getSchool(tenantId);
        Exam exam = examService.getExamOrThrow(tenantId, examId);
        StudentProfileResponse profile = familyService.getProfile(tenantId, studentId);
        StudentProfileResponse.EnrollmentSummary enr = profile.currentEnrollment();

        Map<String, Object> model = new HashMap<>();
        model.put("school", schoolMap(school));
        model.put("exam", Map.of(
            "name", nz(exam.getName()),
            "examType", exam.getExamType() != null ? exam.getExamType().toString() : ""));
        model.put("student", Map.of(
            "displayName", nz(profile.student().displayName()),
            "admissionNumber", nz(profile.student().admissionNumber())));
        model.put("enrollment", enr == null ? null : enrollmentMap(enr));
        model.put("academicYearName", enr != null ? nz(enr.academicYearName()) : "—");

        model.put("subjectRows", subjectRows(examId, studentId));
        model.put("attendance", attendanceSummary(studentId));
        model.put("totalMaxMarks", rc.getTotalMarks());
        model.put("totalObtainedMarks", rc.getObtainedMarks());
        model.put("overallPercentage", rc.getPercentage());
        model.put("overallGrade", rc.getGrade());
        model.put("rankInClass", rc.getRankInClass());
        model.put("classTeacherRemarks", rc.getTeacherRemarks());
        model.put("documentRef", profile.student().admissionNumber() != null
            ? profile.student().admissionNumber() : studentId.toString());
        model.put("issueDate", LocalDate.now());
        return model;
    }

    private Map<String, Object> schoolMap(SchoolResponse s) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", nz(s.name()));
        m.put("city", nz(s.city()));
        m.put("state", nz(s.state()));
        m.put("board", s.board() != null ? s.board().toString() : null);
        m.put("phone", nz(s.phone()));
        m.put("email", nz(s.email()));
        return m;
    }

    private Map<String, Object> enrollmentMap(StudentProfileResponse.EnrollmentSummary enr) {
        Map<String, Object> m = new HashMap<>();
        m.put("className", nz(enr.className()));
        m.put("sectionName", nz(enr.sectionName()));
        m.put("rollNumber", enr.rollNumber());
        m.put("academicYearName", nz(enr.academicYearName()));
        return m;
    }

    private List<Map<String, Object>> subjectRows(UUID examId, UUID studentId) {
        List<ExamMark> marks = markRepository.findByExamIdAndStudentId(examId, studentId);
        Map<UUID, Subject> subjectsById = subjectRepository
            .findAllById(marks.stream().map(ExamMark::getSubjectId).toList())
            .stream().collect(Collectors.toMap(Subject::getId, s -> s));

        List<Map<String, Object>> rows = new ArrayList<>();
        marks.stream()
            .sorted(Comparator.comparing(m -> {
                Subject s = subjectsById.get(m.getSubjectId());
                return s == null ? "" : s.getName();
            }))
            .forEach(m -> {
                Subject subj = subjectsById.get(m.getSubjectId());
                BigDecimal pct = (m.getMaxMarks() != null && m.getMaxMarks().compareTo(BigDecimal.ZERO) > 0
                        && m.getObtainedMarks() != null && !m.isAbsent())
                    ? m.getObtainedMarks().multiply(BigDecimal.valueOf(100))
                        .divide(m.getMaxMarks(), 1, RoundingMode.HALF_UP)
                    : null;
                Map<String, Object> row = new HashMap<>();
                row.put("subjectName", subj != null ? subj.getName() : "(deleted subject)");
                row.put("subjectCode", subj != null ? subj.getCode() : null);
                row.put("maxMarks", m.getMaxMarks());
                row.put("obtainedMarks", m.getObtainedMarks());
                row.put("absent", m.isAbsent());
                row.put("percentage", pct);
                row.put("grade", m.getGrade());
                row.put("remarks", null);
                rows.add(row);
            });
        return rows;
    }

    private Map<String, Object> attendanceSummary(UUID studentId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusYears(1);
        List<AttendanceRecord> recs =
            attendanceRepository.findByStudentIdAndDateBetweenOrderByDateDesc(studentId, from, to);
        long marked = recs.size();
        long present = recs.stream()
            .filter(r -> r.getStatus() == AttendanceStatus.PRESENT
                      || r.getStatus() == AttendanceStatus.LATE
                      || r.getStatus() == AttendanceStatus.HALF_DAY)
            .count();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("marked", marked);
        a.put("present", present);
        a.put("absent", marked - present);
        a.put("percentage", marked > 0
            ? BigDecimal.valueOf(present * 100.0 / marked).setScale(1, RoundingMode.HALF_UP)
            : null);
        return a;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
