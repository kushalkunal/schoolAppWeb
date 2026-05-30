package in.schoolapp.student;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

/**
 * Guards the cross-tenant IDOR fix (audit finding #1). A {@code studentId} from another tenant must
 * be rejected as 404 — never silently accepted, which would let a caller read/associate another
 * school's data.
 */
@ExtendWith(MockitoExtension.class)
class StudentAccessGuardTest {

    @Mock StudentRepository studentRepository;
    @InjectMocks StudentAccessGuard guard;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();

    @Test
    void allowsStudentBelongingToTenant() {
        when(studentRepository.existsByIdAndSchoolId(student, tenantA)).thenReturn(true);
        assertDoesNotThrow(() -> guard.assertInTenant(tenantA, student));
    }

    @Test
    void rejectsStudentFromAnotherTenantAs404() {
        // Student exists, but in tenant B — caller is authenticated for tenant A.
        when(studentRepository.existsByIdAndSchoolId(student, tenantA)).thenReturn(false);

        assertThatThrownBy(() -> guard.assertInTenant(tenantA, student))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void rejectsNullStudentIdWithoutHittingDb() {
        assertThatThrownBy(() -> guard.assertInTenant(tenantB, null))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
