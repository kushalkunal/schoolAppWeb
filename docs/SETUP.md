# Setup guide

From a fresh clone to a passing smoke test in ~10 minutes — zero external accounts needed. All
external providers have a `LOGGING` default that logs to stdout instead of calling a BSP; swap
them in via [INTEGRATIONS.md](INTEGRATIONS.md) when you're ready for production.

---

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | **21** (LTS) | Temurin / Zulu / Oracle — any JDK 21. `java -version` must print 21.x |
| Maven | **3.9+** | **Not on `PATH` by project convention.** Installed at `/c/apache-maven-3.9.12`. Invoke via `/c/apache-maven-3.9.12/bin/mvn` (bash / Git Bash) or `C:\apache-maven-3.9.12\bin\mvn.cmd` (PowerShell). |
| PostgreSQL | **15+** (dev & CI use 16) | Local install or Docker. Default DSN `jdbc:postgresql://localhost:5432/schoolapp`, user `schoolapp`, password `schoolapp_dev_password` |
| Redis | **7** | Local install or Docker. Default `localhost:6379`, no password |
| Docker + Compose | any recent | Optional — easiest way to run Postgres + Redis. |
| `curl` or Postman | any | For smoke testing |

A `docker compose up -d` from `backend/` boots both services with the dev credentials baked into
[application.yml](../backend/src/main/resources/application.yml).

---

## 2. Clone + build

```bash
git clone <repo-url>
cd SchoolManagementSystem/backend

# Maven lives at /c/apache-maven-3.9.12 — not on PATH.
/c/apache-maven-3.9.12/bin/mvn clean test
```

Expected result: **119 tests, 0 failures, 0 errors.**

On first compile, Maven downloads dependencies (~2 minutes). Subsequent runs are ~15 s.

---

## 3. Run the app

```bash
/c/apache-maven-3.9.12/bin/mvn spring-boot:run
```

On first boot Flyway applies **V1 → V5** (initial schema → webhook-correlation columns → at-risk
scores → student documents). Ready in ~6 seconds. If the DB already has a non-Flyway schema,
`baseline-on-migrate=true` stamps `V1` as baseline and moves on.

Health + spec endpoints (no auth required):

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}

curl http://localhost:8080/api/v1/ping
# {"success":true,"data":{"service":"school-management","status":"ok",…}}

open http://localhost:8080/swagger-ui/index.html
# Full API surface; click "Authorize" to paste a JWT.
```

---

## 4. Smoke test — signup → OTP → authenticated call

**Step 1: signup.** Creates a school + principal staff + current academic year.

```bash
TENANT_RESPONSE=$(curl -sS -X POST http://localhost:8080/api/v1/tenants \
  -H 'Content-Type: application/json' \
  -d '{
    "schoolName":"Sunshine Public School",
    "principalName":"Rajesh Kumar",
    "phone":"9876543210",
    "state":"Maharashtra",
    "city":"Pune",
    "board":"CBSE"
  }')
TENANT_ID=$(echo "$TENANT_RESPONSE" | grep -oP '"id":"\K[^"]+' | head -1)
```

**Step 2: request OTP.** With `WHATSAPP_PROVIDER=LOGGING` (default) the OTP is logged to the Spring
Boot console — no BSP needed:

```bash
curl -X POST http://localhost:8080/api/v1/auth/otp/send \
  -H 'Content-Type: application/json' -d '{"phone":"9876543210"}'

# Watch the running app's logs:
# [OTP-DISPATCH] channel=PHONE to=98765****10 otp=123456
# [WA-SEND type=OTP] to=98765****10 body="Your verification code is 123456..."
```

**Step 3: verify + get tokens.**

```bash
AUTH=$(curl -sS -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H 'Content-Type: application/json' \
  -d '{"phone":"9876543210","otp":"123456"}')
ACCESS_TOKEN=$(echo "$AUTH" | grep -oP '"accessToken":"\K[^"]+')
```

**Step 4: authenticated call.**

```bash
curl http://localhost:8080/api/v1/tenants/$TENANT_ID \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Try the same call with a different UUID — `TenantInterceptor` returns 403. Isolation works.

Deeper scripted flows (classes → students → attendance → fees → marks → report cards → migration)
live in [../backend/README.md](../backend/README.md).

---

## 5. Environment variables

Defaults live in [application.yml](../backend/src/main/resources/application.yml). Override any via
env var at runtime. Full provider-level setup: [INTEGRATIONS.md](INTEGRATIONS.md).

### Required in production

| Var | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/schoolapp` | Prod: point at RDS / Neon / Railway |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `schoolapp` / `schoolapp_dev_password` | **Change in prod** |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / empty | Prod: managed Redis |
| `JWT_SECRET` | dev fallback | **Required in prod**, ≥ 64 random chars |
| `OTP_HMAC_SECRET` | dev fallback | **Required in prod**, ≥ 32 random chars |

### Auth tuning

| Var | Default |
|---|---|
| `JWT_ACCESS_TOKEN_EXPIRY_MINUTES` | `15` |
| `JWT_REFRESH_TOKEN_EXPIRY_DAYS` | `7` |
| `OTP_TTL_MINUTES` | `5` |
| `OTP_MAX_ATTEMPTS` | `3` |
| `OTP_MAX_SENDS` / `OTP_RATE_LIMIT_WINDOW_MINUTES` | `3` / `10` |
| `SIGNUP_CHANNEL` | `PHONE` — set `EMAIL` or `BOTH` to accept email signup |

### Logging profile

| Var | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Set to `json` for single-line JSON logs (Loki / CloudWatch friendly) |
| `APP_LOG_LEVEL` | `DEBUG` | Set `INFO` in prod |

### Providers (all optional — `LOGGING` / `LOCAL` are the defaults)

| Var | Default | Purpose |
|---|---|---|
| `WHATSAPP_PROVIDER` | `LOGGING` | `WATI` for real sends — see [INTEGRATIONS.md § WATI](INTEGRATIONS.md#whatsapp-wati) |
| `EMAIL_PROVIDER` | `LOGGING` | `SMTP` for real mail |
| `PAYMENT_PROVIDER` | `LOGGING` | `STRIPE` or `RAZORPAY` |
| `STORAGE_PROVIDER` | `LOCAL` | `S3` for any S3-compatible backend |
| `OCR_PROVIDER` | `LOGGING` | `GOOGLE_CLOUD_VISION` |
| `LLM_PROVIDER` | `LOGGING` | `OPENAI` / `ANTHROPIC` / `GEMINI` |

Per-provider credentials, webhook secrets, and URLs all documented in
[INTEGRATIONS.md](INTEGRATIONS.md).

---

## 6. Tests

```bash
/c/apache-maven-3.9.12/bin/mvn test            # unit + slice tests — 119 pass, no infra
/c/apache-maven-3.9.12/bin/mvn verify          # includes any @SpringBootTest integration tests
/c/apache-maven-3.9.12/bin/mvn clean test      # fresh build, guaranteed reproducible
```

Expected: **119/119 tests passing** in under 30 seconds on a warm machine.

---

## 7. Flyway migrations

Under [backend/src/main/resources/db/migration/](../backend/src/main/resources/db/migration/).
Applied automatically on boot.

| Version | What it adds |
|---|---|
| V1 | Initial schema (27 tables) + `pg_trgm` extension + name trigram index |
| V2 | Case-insensitive unique `staff.email`, unique `staff.phone` |
| V3 | `fee_payments.provider_reference` + unique index (payment webhook idempotency); `notification_log(recipient_phone, created_at)` |
| V4 | `student_risk_scores` |
| V5 | `student_documents` |

**Adding a new migration:** create `V6__<change>.sql`; never edit an existing `Vx` file. Hibernate
`ddl-auto=validate` catches drift on the next boot.

**Resetting the schema in dev:**

```bash
docker compose down -v && docker compose up -d
/c/apache-maven-3.9.12/bin/mvn spring-boot:run
```

---

## 8. Per-module dev guides

See [modules/](modules/) for focused guides — what each module does, where its entities live, and
its external-provider contract.

---

## Troubleshooting

**`mvn: command not found`**
Maven isn't on `PATH` by design. Use `/c/apache-maven-3.9.12/bin/mvn` (Git Bash) or
`C:\apache-maven-3.9.12\bin\mvn.cmd` (PowerShell / cmd).

**App won't start, Flyway: `relation "schools" already exists`**
A previous run left the DB in a broken state. `docker compose down -v && docker compose up -d`.

**`No qualifying bean of type 'XxxProvider' available`**
`app.xxx.provider=SOMETHING` but no `@ConditionalOnProperty` matches. Check the provider class for
its exact `havingValue` and confirm the env var is set on the running process.

**`JavaMailSender` bean missing when `EMAIL_PROVIDER=SMTP`**
Spring's mail auto-config only creates the bean when `spring.mail.host` is set. Provide
`SMTP_HOST` + credentials.

**OCR / LLM timeouts in migration processor**
Google Vision + any LLM can take 5–30 s. Check the `ocrExecutor` pool isn't exhausted (2 core /
4 max). Raise the limits in
[AsyncConfig.java](../backend/src/main/java/in/schoolapp/config/AsyncConfig.java) if multi-tenant
bursts overwhelm it.

**Webhook receives "401 invalid signature"**
The secret on our side doesn't match the BSP / Stripe / Razorpay dashboard. Verify end-to-end:
[INTEGRATIONS.md](INTEGRATIONS.md) has the exact per-provider setup.
