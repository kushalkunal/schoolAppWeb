package in.schoolapp.visitor;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.student.StudentAccessGuard;
import in.schoolapp.student.entity.Parent;
import in.schoolapp.student.entity.StudentParentLink;
import in.schoolapp.student.repository.ParentRepository;
import in.schoolapp.student.repository.StudentParentLinkRepository;
import in.schoolapp.visitor.dto.CreateVisitorRequest;
import in.schoolapp.visitor.entity.Visitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Child-safety gate (audit #16): only a registered guardian may collect a student, else an
 *  audited override is required. */
@ExtendWith(MockitoExtension.class)
class VisitorServiceTest {

    @Mock VisitorRepository repo;
    @Mock StudentAccessGuard studentAccessGuard;
    @Mock ParentRepository parentRepository;
    @Mock StudentParentLinkRepository linkRepository;
    @Mock AuditLogger audit;
    @org.mockito.InjectMocks VisitorService service;

    final UUID tenant = UUID.randomUUID();
    final UUID studentId = UUID.randomUUID();
    final UUID parentId = UUID.randomUUID();

    private CreateVisitorRequest pickup(String phone, String override) {
        return new CreateVisitorRequest(
            "Visitor", phone, "Pickup", null, studentId, true, override, null, null, null);
    }

    @Test
    void registeredGuardianIsAuthorized() {
        lenient().when(repo.save(any(Visitor.class))).thenAnswer(i -> i.getArgument(0));
        Parent parent = new Parent();
        parent.setId(parentId);
        when(parentRepository.findBySchoolIdAndPhone(tenant, "9876543210")).thenReturn(Optional.of(parent));
        StudentParentLink link = new StudentParentLink();
        link.setParentId(parentId);
        when(linkRepository.findByStudentId(studentId)).thenReturn(List.of(link));

        var resp = service.checkIn(tenant, pickup("9876543210", null));

        assertThat(resp.pickupAuthorized()).isTrue();
        verify(audit).logAction(eq(tenant), eq("Visitor"), any(), eq("PICKUP_AUTHORIZED"), any());
    }

    @Test
    void unregisteredPersonWithoutOverrideIsBlocked() {
        when(parentRepository.findBySchoolIdAndPhone(tenant, "9999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkIn(tenant, pickup("9999999999", null)))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.PICKUP_NOT_AUTHORIZED);

        verify(repo, never()).save(any());
    }

    @Test
    void unregisteredPersonWithOverrideIsAllowedAndAudited() {
        lenient().when(repo.save(any(Visitor.class))).thenAnswer(i -> i.getArgument(0));
        when(parentRepository.findBySchoolIdAndPhone(tenant, "9999999999")).thenReturn(Optional.empty());

        var resp = service.checkIn(tenant, pickup("9999999999", "Grandmother, known to staff"));

        assertThat(resp.pickupAuthorized()).isFalse();
        verify(audit).logAction(eq(tenant), eq("Visitor"), any(), eq("PICKUP_OVERRIDE"), any());
    }

    @Test
    void nonPickupVisitSkipsGuardianCheck() {
        lenient().when(repo.save(any(Visitor.class))).thenAnswer(i -> i.getArgument(0));
        CreateVisitorRequest visit = new CreateVisitorRequest(
            "Vendor", "9876543210", "Delivery", null, null, false, null, null, null, null);

        service.checkIn(tenant, visit);

        verify(parentRepository, never()).findBySchoolIdAndPhone(any(), any());
        verify(audit, never()).logAction(any(), any(), any(), any(), any());
    }
}
