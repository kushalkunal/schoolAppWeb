package in.schoolapp.auth;

import in.schoolapp.auth.dto.AuthResponse;
import in.schoolapp.auth.dto.PasswordLoginRequest;
import in.schoolapp.auth.dto.RefreshTokenRequest;
import in.schoolapp.auth.dto.SendOtpRequest;
import in.schoolapp.auth.dto.SetPasswordRequest;
import in.schoolapp.auth.dto.VerifyOtpRequest;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/otp/send")
    public ApiResponse<Map<String, String>> sendOtp(@Valid @RequestBody SendOtpRequest req) {
        authService.sendOtp(req);
        return ApiResponse.success(Map.of("message", "OTP sent"));
    }

    @PostMapping("/otp/verify")
    public ApiResponse<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return ApiResponse.success(authService.verifyOtp(req));
    }

    @PostMapping("/token/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return ApiResponse.success(authService.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout(@RequestBody(required = false) RefreshTokenRequest req) {
        authService.logout(req == null ? null : req.refreshToken());
        return ApiResponse.success(Map.of("message", "Logged out"));
    }

    // ---------- Slice 35 — password login ----------

    /**
     * Public — sign in with phone/email + password. Subject to the same lockout policy as
     * any other primary login. Falls back to OTP if password isn't set.
     */
    @PostMapping("/password/login")
    public ApiResponse<AuthResponse> passwordLogin(@Valid @RequestBody PasswordLoginRequest req) {
        return ApiResponse.success(
            authService.passwordLogin(req.phone(), req.email(), req.password()));
    }

    /**
     * Authenticated — caller must hold a valid JWT (from a fresh OTP verify) and have a
     * verified identifier. Used both for first-time-set and password change.
     */
    @PostMapping("/password/set")
    public ApiResponse<Map<String, String>> setPassword(@Valid @RequestBody SetPasswordRequest req) {
        authService.setPassword(TenantContext.getStaffId(), req.password());
        return ApiResponse.success(Map.of("message", "Password set"));
    }
}
