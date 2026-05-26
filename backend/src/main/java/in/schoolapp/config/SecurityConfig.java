package in.schoolapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.schoolapp.auth.JwtAuthFilter;
import in.schoolapp.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Slice 2: stateless JWT security.
 * <p>
 * Public routes: signup (POST /api/v1/schools), all auth endpoints, actuator health, webhooks
 * (verified separately via HMAC signature). Everything else requires a valid JWT; the
 * {@link JwtAuthFilter} populates the {@code SecurityContext} and {@code TenantContext}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                // HSTS: 1 year, include subdomains, eligible for preload list. Spring Security
                // only emits this header over HTTPS requests — it's correctly a no-op in dev.
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000)
                    .preload(true))
                // CSP: API is JSON-only — no inline scripts, no external resources. The file
                // serving endpoint (/files/**) serves PDFs and images by content-type, so
                // img-src is left permissive for S3 presigned URLs the UI embeds.
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; "
                    + "script-src 'self'; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data: https:; "
                    + "font-src 'self' data:; "
                    + "connect-src 'self'; "
                    + "frame-ancestors 'none'; "
                    + "base-uri 'self'; "
                    + "form-action 'self'"))
                .referrerPolicy(r -> r.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                    "Permissions-Policy",
                    "geolocation=(), microphone=(), camera=(), payment=(), usb=()"))
                // X-Content-Type-Options: nosniff is on by default in Spring Security 6; frame
                // options is set below (DENY — app is never iframed).
                .frameOptions(f -> f.deny())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Slice 35 — password-set is the one /auth path that needs a valid JWT (it
                // changes credentials for the calling staff). Listed BEFORE the catch-all
                // permitAll so Spring picks the more-specific matcher first.
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/password/set").authenticated()
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Public signup — creates a new tenant. Authenticated access lands at
                // GET /api/v1/tenants/{tenantId} and onwards.
                .requestMatchers(HttpMethod.POST, "/api/v1/tenants").permitAll()
                // Slice 17: public admission enquiry — parents land here from a school's
                // marketing site / WhatsApp link. POST only; path includes the school id
                // so the service can scope writes to one tenant without a JWT.
                .requestMatchers(HttpMethod.POST, "/api/v1/public/schools/*/admissions/enquiry").permitAll()
                // Slice 22e: public branding read — frontend deploys fetch this on mount.
                .requestMatchers(HttpMethod.GET, "/api/v1/public/schools/*/branding").permitAll()
                .requestMatchers("/webhooks/**").permitAll()
                // Locally-hosted receipts + report cards. Path segments contain unguessable
                // UUIDs; acceptable for now. Only active when app.storage.provider=LOCAL.
                .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
                .requestMatchers("/api/v1/ping").permitAll()
                // Platform-admin (SaaS slice 1) — JWT required + @PreAuthorize on the
                // controller enforces SUPER_ADMIN. Authentication is enforced here; role
                // check stays in the controller so it's visible alongside the endpoint.
                .requestMatchers("/api/v1/platform/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * Slice 35 — BCrypt for the optional password-login flow. Cost factor 10 is a sensible
     * dev/prod default; bump via Spring property if you ever benchmark hash time on prod hw.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Declared here (not as a @Component) so slice tests that exclude SecurityConfig also skip
     * the filter and its transitive JwtService/Redis dependencies.
     */
    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        return new JwtAuthFilter(jwtService, objectMapper);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Wildcard pattern for localhost on any port — dev convenience, since the web dev
        // server may run on 3000 / 5173 / 61402 / etc. Override via CORS_ORIGINS env if you
        // want to lock this down in prod.
        config.setAllowedOriginPatterns(List.of(
            "https://app.schoolapp.in",
            "http://localhost:*",
            "http://127.0.0.1:*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
