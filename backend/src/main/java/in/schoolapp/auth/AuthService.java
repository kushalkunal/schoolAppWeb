package in.schoolapp.auth;

import in.schoolapp.auth.config.JwtProperties;
import in.schoolapp.auth.config.SignupChannel;
import in.schoolapp.auth.config.SignupProperties;
import in.schoolapp.auth.dto.AuthResponse;
import in.schoolapp.auth.dto.SendOtpRequest;
import in.schoolapp.auth.dto.VerifyOtpRequest;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.dto.StaffResponse;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final OtpService otpService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final StaffRepository staffRepository;
    private final JwtProperties jwtProps;
    private final SignupProperties signupProps;
    private final PasswordEncoder passwordEncoder;

    // Slice 35 — password policy
    private static final int MAX_FAILED_LOGINS = 5;
    private static final int LOCKOUT_MINUTES = 15;
    private static final int MIN_PASSWORD_LEN = 8;

    public void sendOtp(SendOtpRequest req) {
        Identifier id = resolveIdentifier(req.phone(), req.email());
        enforceChannelAllowed(id.type());

        // Gate on actual account existence before burning the OTP slot
        Staff staff = lookupStaff(id)
            .orElseThrow(() -> new AppException(ErrorCode.PHONE_NOT_FOUND,
                "No active account is registered with this " + id.type().name().toLowerCase()));
        log.debug("Sending OTP staffId={} tenantId={} via {}",
            staff.getId(), staff.getSchoolId(), id.type());

        otpService.sendOtp(id.raw(), id.type());
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest req) {
        Identifier id = resolveIdentifier(req.phone(), req.email());
        enforceChannelAllowed(id.type());

        String normalized = otpService.verifyOtp(id.raw(), id.type(), req.otp());
        Staff staff = lookupByNormalized(normalized, id.type())
            .orElseThrow(() -> new AppException(ErrorCode.PHONE_NOT_FOUND,
                "No active account is registered with this " + id.type().name().toLowerCase()));

        // Slice 35 — first successful OTP verifies the identifier and unlocks password setup.
        if (!staff.isIdentifierVerified()) {
            staff.setIdentifierVerified(true);
            staff.setIdentifierVerifiedAt(OffsetDateTime.now());
            staffRepository.save(staff);
        }
        return buildAuthResponse(staff);
    }

    /**
     * Slice 35 — set or change password. Requires a valid JWT in the caller's context (so
     * the staffId is trusted); also requires the identifier to be verified (i.e. the user has
     * completed at least one OTP cycle, blocking pure-password account takeover).
     */
    @Transactional
    public void setPassword(UUID staffId, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LEN) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Password must be at least " + MIN_PASSWORD_LEN + " characters");
        }
        Staff staff = staffRepository.findById(staffId)
            .filter(Staff::isActive)
            .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Account no longer active"));
        if (!staff.isIdentifierVerified()) {
            throw new AppException(ErrorCode.IDENTIFIER_NOT_VERIFIED,
                "Verify your phone or email via OTP before setting a password");
        }
        staff.setPasswordHash(passwordEncoder.encode(newPassword));
        staff.setPasswordSetAt(OffsetDateTime.now());
        staff.setFailedLoginCount(0);
        staff.setLockedUntil(null);
        staffRepository.save(staff);
        log.info("Password set staffId={}", staffId);
    }

    /**
     * Slice 35 — primary password login path. Looks up by phone or email, checks lockout,
     * verifies BCrypt, increments failure counter on miss, issues JWT on hit.
     */
    @Transactional
    public AuthResponse passwordLogin(String phone, String email, String password) {
        Identifier id = resolveIdentifier(phone, email);
        enforceChannelAllowed(id.type());

        Staff staff = lookupStaff(id)
            // Use a generic message — don't tell attackers whether the account exists.
            .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS,
                "Invalid credentials"));

        if (staff.getLockedUntil() != null && staff.getLockedUntil().isAfter(OffsetDateTime.now())) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED,
                "Too many failed attempts. Try again later or sign in with OTP.");
        }
        if (staff.getPasswordHash() == null || staff.getPasswordHash().isBlank()) {
            throw new AppException(ErrorCode.PASSWORD_NOT_SET,
                "Password not set. Sign in with OTP and set one from your profile.");
        }
        if (!passwordEncoder.matches(password == null ? "" : password, staff.getPasswordHash())) {
            staff.setFailedLoginCount(staff.getFailedLoginCount() + 1);
            if (staff.getFailedLoginCount() >= MAX_FAILED_LOGINS) {
                staff.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                staff.setFailedLoginCount(0);
                log.warn("Account locked staffId={} for {} minutes", staff.getId(), LOCKOUT_MINUTES);
            }
            staffRepository.save(staff);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
        }

        // Success — clear failure counter, lockout, and issue tokens.
        if (staff.getFailedLoginCount() != 0 || staff.getLockedUntil() != null) {
            staff.setFailedLoginCount(0);
            staff.setLockedUntil(null);
            staffRepository.save(staff);
        }
        return buildAuthResponse(staff);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String rawRefreshToken) {
        UUID staffId = refreshTokenService.rotate(rawRefreshToken);
        Staff staff = staffRepository.findById(staffId)
            .filter(Staff::isActive)
            .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Account no longer active"));
        return buildAuthResponse(staff);
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    // ---- internals ----

    private AuthResponse buildAuthResponse(Staff staff) {
        String accessToken = jwtService.issueAccessToken(staff);
        String refreshToken = refreshTokenService.issue(staff.getId());
        long expiresIn = jwtProps.accessTokenExpiryMinutes() * 60L;
        log.info("Login successful staffId={} tenantId={} role={}",
            staff.getId(), staff.getSchoolId(), staff.getRole());
        return new AuthResponse(accessToken, refreshToken, expiresIn, StaffResponse.from(staff));
    }

    private Identifier resolveIdentifier(String phone, String email) {
        boolean hasPhone = phone != null && !phone.isBlank();
        boolean hasEmail = email != null && !email.isBlank();
        if (hasPhone == hasEmail) {  // both or neither
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Exactly one of phone or email must be provided");
        }
        return hasPhone
            ? new Identifier(phone, IdentifierType.PHONE)
            : new Identifier(email, IdentifierType.EMAIL);
    }

    private void enforceChannelAllowed(IdentifierType type) {
        SignupChannel channel = signupProps.channel();
        boolean allowed = switch (type) {
            case PHONE -> channel.allowsPhone();
            case EMAIL -> channel.allowsEmail();
        };
        if (!allowed) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Login via " + type + " is disabled (signup channel: " + channel + ")");
        }
    }

    private java.util.Optional<Staff> lookupStaff(Identifier id) {
        // Normalise lazily — the OtpService will normalise again, but that's cheap and idempotent.
        return switch (id.type()) {
            case PHONE -> staffRepository.findByPhoneAndActiveTrue(
                in.schoolapp.common.PhoneNormalizer.normalize(id.raw()));
            case EMAIL -> staffRepository.findByEmailAndActiveTrue(
                in.schoolapp.common.EmailNormalizer.normalize(id.raw()));
        };
    }

    private java.util.Optional<Staff> lookupByNormalized(String normalized, IdentifierType type) {
        return switch (type) {
            case PHONE -> staffRepository.findByPhoneAndActiveTrue(normalized);
            case EMAIL -> staffRepository.findByEmailAndActiveTrue(normalized);
        };
    }

    private record Identifier(String raw, IdentifierType type) {}
}
