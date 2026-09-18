package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.ExamScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface ExamScheduleRepository extends JpaRepository<ExamScheduleEntry, UUID> {

    List<ExamScheduleEntry> findByExamIdOrderByExamDateAscStartTimeAsc(UUID examId);

    @Transactional
    void deleteByExamId(UUID examId);
}
