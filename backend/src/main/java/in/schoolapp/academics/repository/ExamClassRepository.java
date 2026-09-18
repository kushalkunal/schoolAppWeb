package in.schoolapp.academics.repository;

import in.schoolapp.academics.entity.ExamClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface ExamClassRepository extends JpaRepository<ExamClass, ExamClass.Key> {

    List<ExamClass> findByExamId(UUID examId);

    @Transactional
    void deleteByExamId(UUID examId);
}
