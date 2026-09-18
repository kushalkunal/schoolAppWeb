package in.schoolapp.academics;

import in.schoolapp.academics.dto.ExamScheduleDtos.ScheduleRow;
import in.schoolapp.academics.dto.ExamScheduleDtos.UpsertScheduleRequest;
import in.schoolapp.academics.entity.ExamScheduleEntry;
import in.schoolapp.academics.repository.ExamScheduleRepository;
import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Manages an exam's examination timetable (one sitting per subject). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamScheduleService {

    private final ExamScheduleRepository scheduleRepository;
    private final SubjectRepository subjectRepository;
    private final ExamService examService;

    @Transactional(readOnly = true)
    public List<ScheduleRow> getSchedule(UUID tenantId, UUID examId) {
        examService.getExamOrThrow(tenantId, examId);
        List<ExamScheduleEntry> entries = scheduleRepository.findByExamIdOrderByExamDateAscStartTimeAsc(examId);
        Map<UUID, String> names = subjectNames(entries.stream().map(ExamScheduleEntry::getSubjectId).toList());
        return entries.stream()
            .map(e -> new ScheduleRow(e.getSubjectId(), names.getOrDefault(e.getSubjectId(), "—"),
                e.getExamDate(), e.getStartTime(), e.getEndTime()))
            .toList();
    }

    /** Replace-all upsert of the exam's sittings. */
    @Transactional
    public List<ScheduleRow> upsert(UUID tenantId, UUID examId, UpsertScheduleRequest req) {
        examService.getExamOrThrow(tenantId, examId);
        scheduleRepository.deleteByExamId(examId);
        for (var s : req.sittings()) {
            ExamScheduleEntry e = new ExamScheduleEntry();
            e.setSchoolId(tenantId);
            e.setExamId(examId);
            e.setSubjectId(s.subjectId());
            e.setExamDate(s.examDate());
            e.setStartTime(s.startTime());
            e.setEndTime(s.endTime());
            scheduleRepository.save(e);
        }
        log.info("Saved exam schedule exam={} sittings={}", examId, req.sittings().size());
        return getSchedule(tenantId, examId);
    }

    private Map<UUID, String> subjectNames(List<UUID> subjectIds) {
        if (subjectIds.isEmpty()) return Map.of();
        return subjectRepository.findAllById(subjectIds).stream()
            .collect(Collectors.toMap(Subject::getId, Subject::getName, (a, b) -> a));
    }
}
