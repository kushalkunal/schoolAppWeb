package in.schoolapp.analytics.repository;

import in.schoolapp.analytics.entity.StudentRiskScore;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRiskScoreRepository extends JpaRepository<StudentRiskScore, UUID> {

    Optional<StudentRiskScore> findBySchoolIdAndStudentId(UUID schoolId, UUID studentId);

    /** Highest-score students first — drives the dashboard "Top N at risk" tile. */
    List<StudentRiskScore> findBySchoolIdOrderByScoreDesc(UUID schoolId, Pageable pageable);

    long countBySchoolIdAndScoreGreaterThanEqual(UUID schoolId, int scoreThreshold);

    /** Slice 19b: only rows above a threshold, paged + sorted descending. */
    List<StudentRiskScore> findBySchoolIdAndScoreGreaterThanEqualOrderByScoreDesc(
        UUID schoolId, int scoreThreshold, Pageable pageable);
}
