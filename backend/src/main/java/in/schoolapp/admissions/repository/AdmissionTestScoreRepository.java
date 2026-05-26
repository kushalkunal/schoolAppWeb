package in.schoolapp.admissions.repository;

import in.schoolapp.admissions.entity.AdmissionTestScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdmissionTestScoreRepository extends JpaRepository<AdmissionTestScore, UUID> {

    List<AdmissionTestScore> findByAdmissionIdOrderBySubjectName(UUID admissionId);
}
