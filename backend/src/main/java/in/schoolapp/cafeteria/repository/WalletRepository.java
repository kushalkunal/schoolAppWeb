package in.schoolapp.cafeteria.repository;

import in.schoolapp.cafeteria.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByIdAndSchoolId(UUID id, UUID schoolId);
    Optional<Wallet> findBySchoolIdAndStudentId(UUID schoolId, UUID studentId);
}
