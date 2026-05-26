package in.schoolapp.student;

import in.schoolapp.common.PhoneNormalizer;
import in.schoolapp.student.entity.Parent;
import in.schoolapp.student.repository.ParentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Powers sibling auto-detection: when a student's parent phone matches an existing Parent in
 * this tenant, we reuse the record — the two students become siblings via the
 * {@code student_parent_links} join table.
 */
@Service
@RequiredArgsConstructor
public class ParentService {

    private final ParentRepository parentRepository;

    /**
     * Returns the existing Parent with this phone, or creates a new one. Name/email are only
     * written on creation — existing Parent records are not clobbered on re-link.
     */
    @Transactional
    public Parent findOrCreate(UUID schoolId, String rawPhone, String name, String email) {
        String phone = PhoneNormalizer.normalize(rawPhone);
        return parentRepository.findBySchoolIdAndPhone(schoolId, phone)
            .orElseGet(() -> {
                Parent p = new Parent();
                p.setSchoolId(schoolId);
                p.setPhone(phone);
                p.setName(name == null || name.isBlank() ? "Parent of " + phone : name.trim());
                p.setEmail(email == null || email.isBlank() ? null : email.trim().toLowerCase());
                return parentRepository.save(p);
            });
    }
}
