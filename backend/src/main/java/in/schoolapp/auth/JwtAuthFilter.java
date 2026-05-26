package in.schoolapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.common.ApiResponse;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Parses the JWT from {@code Authorization: Bearer <token>}, sets the Spring Security context,
 * and populates {@link TenantContext} so every downstream service/controller ca read the caller's
 * school and staff id without re-parsing the token.n
 * <p>
 * Missing token is not an error — the filter chain falls through and the security config
 * decides whether the route is public. Malformed/expired tokens, though, short-circuit with a
 * JSON error body so clients don't receive a stack trace.
 * <p>
 * Registered as a {@code @Bean} in SecurityConfig (not a {@code @Component}) so it loads only
 * when the security config loads — keeping {@code @WebMvcTest} slices from pulling in
 * {@code JwtService} and Redis beans they don't need.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length()).trim();
        try {
            JwtService.JwtClaims claims = jwtService.parse(token);

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()));
            var auth = new UsernamePasswordAuthenticationToken(claims.staffId(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
            TenantContext.set(claims.tenantId(), claims.staffId(), claims.role());

            try {
                chain.doFilter(request, response);
            } finally {
                // Always clear at the end of the request, even if the downstream chain threw.
                // Prevents ThreadLocal leak when Tomcat recycles the thread for a different user.
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
        } catch (AppException e) {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
            writeErrorResponse(response, e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected JWT parse failure", e);
            SecurityContextHolder.clearContext();
            TenantContext.clear();
            writeErrorResponse(response, ErrorCode.TOKEN_INVALID, "Invalid access token");
        }
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, message)));
    }
}
