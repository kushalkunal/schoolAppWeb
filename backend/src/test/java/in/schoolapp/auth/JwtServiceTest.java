package in.schoolapp.auth;

import in.schoolapp.auth.config.JwtProperties;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private Staff staff;

    @BeforeEach
    void setUp() {
        // 64+ char secret to satisfy HMAC-SHA256's minimum 256-bit key size
        JwtProperties props = new JwtProperties(
            "test-secret-test-secret-test-secret-test-secret-test-secret-test-secret",
            15, 7);
        jwtService = new JwtService(props);

        staff = new Staff();
        staff.setId(UUID.randomUUID());
        staff.setSchoolId(UUID.randomUUID());
        staff.setFirstName("Rajesh");
        staff.setLastName("Kumar");
        staff.setPhone("9876543210");
        staff.setRole(StaffRole.PRINCIPAL);
    }

    @Test
    void issueAndParse_roundTripsClaims() {
        String token = jwtService.issueAccessToken(staff);
        JwtService.JwtClaims claims = jwtService.parse(token);

        assertThat(claims.staffId()).isEqualTo(staff.getId());
        assertThat(claims.tenantId()).isEqualTo(staff.getSchoolId());
        assertThat(claims.role()).isEqualTo("PRINCIPAL");
        assertThat(claims.name()).isEqualTo("Rajesh Kumar");
    }

    @Test
    void parse_rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.parse("not.a.valid.token"))
            .isInstanceOf(AppException.class)
            .matches(e -> ((AppException) e).getErrorCode() == ErrorCode.TOKEN_INVALID);
    }

    @Test
    void parse_rejectsTokenSignedWithDifferentSecret() {
        JwtProperties otherProps = new JwtProperties(
            "different-secret-different-secret-different-secret-different-secret",
            15, 7);
        JwtService other = new JwtService(otherProps);
        String foreignToken = other.issueAccessToken(staff);

        assertThatThrownBy(() -> jwtService.parse(foreignToken))
            .isInstanceOf(AppException.class)
            .matches(e -> ((AppException) e).getErrorCode() == ErrorCode.TOKEN_INVALID);
    }
}
