package in.schoolapp.infirmary;

import in.schoolapp.infirmary.entity.StudentMedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentMedicalRecordRepository extends JpaRepository<StudentMedicalRecord, UUID> {}
