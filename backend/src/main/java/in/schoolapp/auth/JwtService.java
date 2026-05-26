package in.schoolapp.auth;

import in.schoolapp.auth.config.JwtProperties;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.entity.Staff;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtService(JwtProperties props) {
        this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(props.accessTokenExpiryMinutes());
    }

    public String issueAccessToken(Staff staff) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(staff.getId().toString())
            .claim("tenantId", staff.getSchoolId().toString())
            .claim("role", staff.getRole().name())
            .claim("name", staff.displayName())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenTtl)))
            .signWith(signingKey)
            .compact();
    }

    public JwtClaims parse(String token) {
        try {
            Claims c = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            return new JwtClaims(
                UUID.fromString(c.getSubject()),
                UUID.fromString(c.get("tenantId", String.class)),
                c.get("role", String.class),
                c.get("name", String.class),
                c.getExpiration().toInstant()
            );
        } catch (ExpiredJwtException e) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED, "Access token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new AppException(ErrorCode.TOKEN_INVALID, "Access token is invalid");
        }
    }

    public record JwtClaims(UUID staffId, UUID tenantId, String role, String name, Instant expiresAt) {}
}
