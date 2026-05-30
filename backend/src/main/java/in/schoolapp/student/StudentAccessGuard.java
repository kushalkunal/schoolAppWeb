package in.schoolapp.student;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The frontline guard against cross-tenant IDOR on endpoints that accept a caller-supplied
 * {@code studentId}.
 * <p>
 * The {@code TenantInterceptor} only validates the {@code {tenantId}} <em>path</em> segment against
 * the JWT; it cannot know whether an arbitrary {@code studentId} argument belongs to that tenant.
 * Any service that loads or associates child data by raw {@code studentId} must first call
 * {@link #assertInTenant(UUID, UUID)} — otherwise a user authenticated for their own school can
 * read or write another school's student data simply by passing a foreign id.
 * <p>
 * Because a {@code studentId} is globally unique to exactly one school, confirming the student
 * belongs to the tenant is sufficient: every child row keyed by that {@code studentId} then
 * provably belongs to the same tenant.
 * <p>
 * Throws {@code 404 RESOURCE_NOT_FOUND} (not {@code 403}) on failure so the response never reveals
 * that the id exists in some other tenant.
 */
@Component
@RequiredArgsConstructor
public class StudentAccessGuard {

    private final StudentRepository studentRepository;

    public void assertInTenant(UUID tenantId, UUID studentId) {
        if (studentId == null || tenantId == null
            || !studentRepository.existsByIdAndSchoolId(studentId, tenantId)) {
            throw AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Student", studentId);
        }
    }
}
