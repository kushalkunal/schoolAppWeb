# `auth` module

OTP-based login, JWT access tokens, rotating opaque refresh tokens, and the role/permission constants that every other module's `@PreAuthorize` expression references.

**Package:** `in.schoolapp.auth`

---

## Purpose

Schools in the target market run on phone numbers, not passwords — passwords are forgotten, reset flows are a support burden, and the typical user already uses WhatsApp daily. The auth module issues a 6-digit OTP to a registered phone (or email, behind a feature flag), stores an HMAC-SHA256 hash of the code in Redis with a 5-minute TTL, and on verify mints a short-lived JWT plus an opaque UUID refresh token. Refresh tokens rotate on every use and are stored in Redis as SHA-256 hashes keyed on the hash itself, so a Redis dump never leaks valid tokens. Access-token propagation is stateless: `JwtAuthFilter` parses the Bearer header, sets the Spring Security context with a `ROLE_<name>` authority, and populates `TenantContext` so downstream services can read the caller's tenant + staff id without re-parsing.

No entities or tables — session state lives in Redis only.

---

## Services and key methods

| File | Key methods | Notes |
|---|---|---|
| [`AuthService`](../../backend/src/main/java/in/schoolapp/auth/AuthService.java) | `sendOtp`, `verifyOtp`, `refresh`, `logout` | Orchestrator; enforces the `app.signup.channel` feature flag and gates on Staff row existence before burning an OTP slot. |
| [`OtpService`](../../backend/src/main/java/in/schoolapp/auth/OtpService.java) | `sendOtp(identifier, type)`, `verifyOtp(identifier, type, submittedOtp)` | Per-identifier throttle via Redis `INCR` + TTL (`app.otp.max-sends-per-window` over `rate-limit-window-minutes`). HMAC-SHA256 hashes the OTP with `app.otp.hmac-secret`. 3 verify attempts before the key is burned. |
| [`JwtService`](../../backend/src/main/java/in/schoolapp/auth/JwtService.java) | `issueAccessToken(Staff)`, `parse(token)` | HS256 signed; claims: `sub=staffId`, `tenantId`, `role`, `name`. |
| [`RefreshTokenService`](../../backend/src/main/java/in/schoolapp/auth/RefreshTokenService.java) | `issue(staffId)`, `rotate(rawToken)`, `revoke(rawToken)` | Raw token is a UUID (122 bits). Redis key is `refresh:<sha256(raw)>` → staffId, TTL = `app.jwt.refresh-token-expiry-days`. Rotate = lookup + delete + caller re-issues; `rotate` is atomic from the caller's perspective because the delete happens before the new `issue`. |
| [`JwtAuthFilter`](../../backend/src/main/java/in/schoolapp/auth/JwtAuthFilter.java) | `doFilterInternal` | Parses `Authorization: Bearer ...`. Missing header is not an error (the security config decides whether the route is public). Malformed/expired tokens short-circuit with a JSON `ApiResponse.error` body instead of a stack trace. Always clears `SecurityContextHolder` + `TenantContext` in a `finally` block — prevents ThreadLocal leaks when Tomcat recycles threads across users. |

### `AppRoles` — role constants for `@PreAuthorize`

[`AppRoles`](../../backend/src/main/java/in/schoolapp/auth/AppRoles.java) holds pre-built `hasAnyRole(...)` string literals that every mutating controller in the codebase imports. Annotation expressions can't concatenate, so the strings are pre-assembled:

| Constant | Expansion |
|---|---|
| `OWNER_OR_ADMIN` | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN')` |
| `ANY_TEACHER` | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER','SUBJECT_TEACHER')` |
| `FEE_WRITER` | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','ACCOUNTANT')` |
| `ATTENDANCE_WRITER` | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER')` |
| `MARKS_WRITER` | `hasAnyRole('SCHOOL_OWNER','PRINCIPAL','ADMIN','CLASS_TEACHER','SUBJECT_TEACHER')` |

`JwtAuthFilter` prefixes the JWT's `role` claim with `ROLE_` before adding it to the authentication token, so the bare names in `AppRoles` line up with what Spring's `hasRole` / `hasAnyRole` expects. The source enum is [`school.entity.StaffRole`](../../backend/src/main/java/in/schoolapp/school/entity/StaffRole.java) — keep these in sync.

`@EnableMethodSecurity` is declared on [`SecurityConfig`](../../backend/src/main/java/in/schoolapp/config/SecurityConfig.java); `@PreAuthorize` is applied to all mutating endpoints across the app (51 occurrences across 17 controllers at last count).

---

## Endpoints

All under `/api/v1/auth/**`. Public (no JWT required) — see [`AuthController`](../../backend/src/main/java/in/schoolapp/auth/AuthController.java).

| Path | Method | `@PreAuthorize` | Purpose |
|---|---|---|---|
| `/api/v1/auth/otp/send` | POST | none (public) | Issue OTP to a registered phone/email |
| `/api/v1/auth/otp/verify` | POST | none (public) | Exchange OTP for `{accessToken, refreshToken, staff}` |
| `/api/v1/auth/token/refresh` | POST | none (public) | Rotate refresh token → new access + refresh pair |
| `/api/v1/auth/logout` | POST | none (public) | Revoke the supplied refresh token |

`OtpService` has its own per-identifier throttle, so `/api/v1/auth/**` is exempted from the general `RateLimitFilter` — see below.

---

## SecurityConfig

[`SecurityConfig`](../../backend/src/main/java/in/schoolapp/config/SecurityConfig.java) wires stateless JWT auth and a hardened response-header stack:

- **Session policy:** `STATELESS` — no `JSESSIONID`, no server-side session.
- **CSRF:** disabled (stateless JWT API; CSRF tokens would be dead weight).
- **CORS:** configured source allowing `app.schoolapp.in`, `localhost:3000`, `localhost:5173`; credentials enabled; `X-Request-Id` exposed.
- **HSTS:** `max-age=31_536_000` (1 year), `includeSubDomains`, `preload`. Spring emits this only over HTTPS requests, so it's a correct no-op in dev.
- **CSP:** `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`. API is JSON-only, so CSP is mostly defensive; `img-src` is permissive for S3 presigned URLs the UI embeds.
- **Referrer-Policy:** `STRICT_ORIGIN_WHEN_CROSS_ORIGIN`.
- **Permissions-Policy:** `geolocation=(), microphone=(), camera=(), payment=(), usb=()`.
- **Frame options:** `DENY` — the app is never legitimately iframed.
- **X-Content-Type-Options:** `nosniff` (Spring Security 6 default).

Public routes explicitly allowed: `OPTIONS /**`, actuator health/info/prometheus, Swagger UI + API docs, `/api/v1/auth/**`, `POST /api/v1/tenants` (public signup), `/webhooks/**` (HMAC-verified per-webhook), `GET /files/**` (LOCAL storage serving), `/api/v1/ping`. Everything else requires a valid JWT.

`JwtAuthFilter` is registered as a `@Bean` (not a `@Component`) so slice tests that exclude `SecurityConfig` automatically skip the filter and its transitive `JwtService` / Redis dependencies.

---

## Rate limiting

[`RateLimitFilter`](../../backend/src/main/java/in/schoolapp/config/RateLimitFilter.java) (in the `config` package — listed here because the JWT filter populates the key it uses) is a simple token-bucket limiter: 600 requests per 60-second window keyed on the authenticated staff id (falls back to `X-Forwarded-For` / `RemoteAddr` for pre-auth requests). In-memory + single-instance — adequate for the single-node Phase-1 deployment. Skips `/actuator/`, `/webhooks/` (BSPs hit these by design), `/api/v1/auth/` (OTP has its own throttle), and `/api/v1/ping`.

Configurable via `app.rate-limit.capacity`, `app.rate-limit.window-seconds`, `app.rate-limit.enabled`.

---

## JWT claim shape

```json
{
  "sub": "<staff UUID>",
  "tenantId": "<school UUID>",
  "role": "PRINCIPAL",
  "name": "Rajesh Kumar",
  "iat": 1713700000,
  "exp": 1713700900
}
```

Claim is `tenantId`, not `schoolId` — matches the URL vocabulary. `TenantContext.getTenantId()` returns this value; `TenantContext.getStaffId()` returns `sub`.

---

## Design decisions

- **OTP is HMAC-hashed, not bcrypt/argon2.** OTPs have tiny entropy (6 digits) but also tiny TTL (5 min) and a hard attempt cap (3). A fast keyed hash protects against Redis dump leakage without paying per-verify latency. Slow hashes would add nothing over the attempt cap.
- **Refresh tokens rotate every use.** `RefreshTokenService.rotate` deletes the old hash and the caller re-issues. A leaked refresh token gets invalidated the moment the real user refreshes next, bounding the exposure window.
- **Access token denylist not needed (yet).** Access TTL is 15 minutes; a compromised access token lives only that long. Logout invalidates the refresh token only. A `jti` denylist can be added to Redis if tighter revocation is ever needed.
- **Signup channel feature flag.** [`SignupProperties`](../../backend/src/main/java/in/schoolapp/auth/config/SignupProperties.java) and [`SignupChannel`](../../backend/src/main/java/in/schoolapp/auth/config/SignupChannel.java) gate which identifier types can sign up and log in — `PHONE`, `EMAIL`, or `BOTH`. `AuthService.enforceChannelAllowed` rejects mismatched login attempts with a 400.
- **Lookup before OTP dispatch.** `AuthService.sendOtp` resolves the Staff row before `OtpService.sendOtp` is called — a wrong phone never burns an OTP slot and never dispatches.
- **ThreadLocal hygiene.** `JwtAuthFilter` always clears `SecurityContextHolder` and `TenantContext` in a `finally` block, even on exceptions. Tomcat recycles threads across requests, and a stale tenant in `TenantContext` would leak across users.
- **Webhook secrets are verified inside the webhook controller**, not here — `/webhooks/**` is in the Security permit list, and per-endpoint HMAC verification lives in the webhook classes.

---

## Configuration

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}                 # ≥32 chars
    access-token-expiry-minutes: 15
    refresh-token-expiry-days: 7
  otp:
    ttl-minutes: 5
    max-verify-attempts: 3
    max-sends-per-window: 3
    rate-limit-window-minutes: 10
    hmac-secret: ${OTP_HMAC_SECRET}       # ≥16 chars
  signup:
    channel: PHONE                        # PHONE | EMAIL | BOTH
  rate-limit:
    capacity: 600
    window-seconds: 60
    enabled: true
```

Property binding records: [`JwtProperties`](../../backend/src/main/java/in/schoolapp/auth/config/JwtProperties.java), [`OtpProperties`](../../backend/src/main/java/in/schoolapp/auth/config/OtpProperties.java), [`SignupProperties`](../../backend/src/main/java/in/schoolapp/auth/config/SignupProperties.java), [`SignupChannel`](../../backend/src/main/java/in/schoolapp/auth/config/SignupChannel.java).

---

## Cross-module calls

- **Reads:** `school.StaffRepository` to resolve phone/email → Staff, and `school.dto.StaffResponse` for the login response payload.
- **Dispatches via:** `communication.OtpDispatcher` → routes PHONE to `WhatsAppNotifier`, EMAIL to `EmailSender`.
- **Called by:** every other controller indirectly via `JwtAuthFilter` (sets up `TenantContext`). Direct dependents: none — this module is a leaf on the call graph.
- **Publishes events:** none.
- **Consumes events:** none.

---

## Migrations

- **V1** — seeds the `staff` and `schools` tables that OTP login looks up.
- **V2** — drops `NOT NULL` on `schools.phone` and `staff.phone` to allow email-only signup, and adds globally-unique partial indexes: `uq_staff_phone_global`, `uq_staff_email`, `uq_schools_email`. These indexes are what lets the login flow assume at-most-one Staff per identifier.
