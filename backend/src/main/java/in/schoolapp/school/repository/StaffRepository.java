package in.schoolapp.school.repository;

import in.schoolapp.school.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<Staff, UUID> {

    /**
     * Used by OTP login. Phone is only unique within a school at the DB level — if the same
     * phone exists in multiple schools (edge case for Phase 1), this returns the first.
     * Multi-school staff will be handled post-MVP.
     */
    Optional<Staff> findByPhoneAndActiveTrue(String phone);

    Optional<Staff> findByEmailAndActiveTrue(String email);

    Optional<Staff> findBySchoolIdAndPhone(UUID schoolId, String phone);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    List<Staff> findBySchoolIdAndActiveTrueOrderByFirstName(UUID schoolId);
}
