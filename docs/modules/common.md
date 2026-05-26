# `common` module

Foundational utilities used by every other module. No business logic, no external deps — this is the
small, stable base layer every other package sits on.

**Package:** `in.schoolapp.common`

---

## What's here

| File | Purpose |
|---|---|
| [`BaseEntity`](../../backend/src/main/java/in/schoolapp/common/BaseEntity.java) | `@MappedSuperclass` with `id`, `schoolId` (nullable=false, updatable=false), `createdAt`, `updatedAt`, `createdByStaffId` — every domain table extends it |
| [`TenantContext`](../../backend/src/main/java/in/schoolapp/common/TenantContext.java) | `ThreadLocal` identity store (`tenantId`, `staffId`, `role`) + `validateTenant(expected)` |
| [`TenantInterceptor`](../../backend/src/main/java/in/schoolapp/common/TenantInterceptor.java) | Auto-verifies `{tenantId}` path variable against the JWT claim on every tenant-scoped request |
| [`ApiResponse`](../../backend/src/main/java/in/schoolapp/common/ApiResponse.java) | Canonical `{success, data, error, meta}` envelope (nulls elided) |
| [`ErrorCode`](../../backend/src/main/java/in/schoolapp/common/ErrorCode.java) | Enum of machine-readable error codes mapped to HTTP status |
| [`AppException`](../../backend/src/main/java/in/schoolapp/common/AppException.java) | Single exception type thrown at service boundaries; carries `ErrorCode` + message + optional details |
| [`GlobalExceptionHandler`](../../backend/src/main/java/in/schoolapp/common/GlobalExceptionHandler.java) | `@RestControllerAdvice` — maps `AppException`, validation failures, auth failures, unhandled exceptions to `ApiResponse` errors |
| [`PhoneNormalizer`](../../backend/src/main/java/in/schoolapp/common/PhoneNormalizer.java) | Indian phone → 10-digit canonical form; `mask()` for logs |
| [`EmailNormalizer`](../../backend/src/main/java/in/schoolapp/common/EmailNormalizer.java) | Trim + lowercase; permissive regex validation |
| [`PingController`](../../backend/src/main/java/in/schoolapp/common/PingController.java) | `GET /api/v1/ping` — stack-alive smoke test |

---

## Per-request identity — `TenantContext`

Set by [`JwtAuthFilter`](../../backend/src/main/java/in/schoolapp/auth/JwtAuthFilter.java) after token
validation; cleared in a `finally` block so the `ThreadLocal` can't leak across pooled request
threads. `TenantInterceptor.afterCompletion` clears again as defence-in-depth.

```java
TenantContext.set(tenantId, staffId, role);
try { chain.doFilter(req, res); }
finally { TenantContext.clear(); }
```

Any service reads the current identity directly:

```java
UUID tenant = TenantContext.getTenantId();
UUID staff  = TenantContext.getStaffId();
String role = TenantContext.getRole();
```

`validateTenant(expected)` throws `AppException(UNAUTHORIZED)` if no identity is set and
`AppException(FORBIDDEN)` when the authenticated tenant doesn't match the path parameter. The
`TenantInterceptor` calls this automatically whenever a request's URI template contains the
`tenantId` path variable — controllers never have to remember.

---

## `AppException` + `ErrorCode`

Services throw domain errors once; the `GlobalExceptionHandler` converts them into the canonical
envelope with the right HTTP status:

```java
throw new AppException(ErrorCode.STUDENT_NOT_FOUND, "Student " + id + " does not exist");
throw AppException.notFound(ErrorCode.SECTION_NOT_FOUND, "Section", sectionId);
```

Wire response:

```json
{"success": false, "error": {"code": "STUDENT_NOT_FOUND", "message": "Student … does not exist"}}
```

`AppException` is logged at `INFO` (it's an expected domain signal, not a bug); the catch-all
`Exception` handler logs at `ERROR` and returns a generic `INTERNAL_ERROR` so stack traces never
reach clients.

---

## Normalizers at every boundary

Every phone number flowing in from an HTTP request, an LLM extraction, or a webhook passes
through `PhoneNormalizer.normalize()` before lookup or dispatch. Handles `+91 98765 43210`,
`919876543210`, `09876543210`, `98765-43210` — all collapse to `9876543210`. `PhoneNormalizer.mask`
produces `98765****10` for logs so request bodies don't leak raw phones into stdout.

`EmailNormalizer.normalize()` trims and lowercases; `validate()` applies a pragmatic regex that
rejects obvious garbage without pretending to verify deliverability.

---

## `ApiResponse<T>` envelope

Every controller returns `ApiResponse<DTO>`; `@JsonInclude(NON_NULL)` elides empty `data`, `error`,
or `meta` fields so clients get a minimal shape. `Meta` carries pagination state (`total`, `page`,
`limit`, `nextCursor`) when applicable.

---

## How to test locally

No infrastructure. Focused unit tests:

```bash
mvn -f backend/pom.xml test -Dtest=PhoneNormalizerTest
mvn -f backend/pom.xml test -Dtest=EmailNormalizerTest
mvn -f backend/pom.xml test -Dtest=TenantContextTest
```

The full suite (119 tests) runs with `mvn -f backend/pom.xml clean test`.
