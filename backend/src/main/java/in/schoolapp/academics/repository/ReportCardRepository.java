package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.ReportCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportCardRepository extends JpaRepository<ReportCard, UUID> {

    Optional<ReportCard> findByStudentIdAndExamId(UUID studentId, UUID examId);

    List<ReportCard> findByExamId(UUID examId);

    List<ReportCard> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
}
