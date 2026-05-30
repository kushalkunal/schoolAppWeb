package in.schoolapp.student;

import in.schoolapp.auth.AppRoles;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.student.dto.WithdrawRequest;
import in.schoolapp.student.dto.WithdrawalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/students")
@RequiredArgsConstructor
public class StudentWithdrawalController {

    private final StudentWithdrawalService withdrawalService;

    @PostMapping("/{studentId}/withdraw")
    @PreAuthorize(AppRoles.OWNER_OR_ADMIN)
    public ApiResponse<WithdrawalResponse> withdraw(
        @PathVariable UUID tenantId,
        @PathVariable UUID studentId,
        @Valid @RequestBody WithdrawRequest req
    ) {
        return ApiResponse.success(
            withdrawalService.withdraw(tenantId, studentId, req.reason(), req.overrideDues(), req.leavingDate()));
    }
}
