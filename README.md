# Nerya All Naturals — Backend

Spring Boot 3 / Java 21 e-commerce API (catalog, cart, checkout, payments, reviews, blog CMS) backed by MySQL + Flyway.

## Running locally

```bash
./gradlew bootRun
```

Requires a MySQL 8 instance and the environment variables below. `JWT_SECRET` and at least one `ADMIN_*` set are mandatory — the app fails fast at startup without them.

## Environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_HOST` | no | `localhost` | MySQL host |
| `DB_PORT` | no | `3306` | MySQL port |
| `DB_NAME` | no | `testdb` | Database name (auto-created) |
| `DB_USERNAME` | yes | — | MySQL username |
| `DB_PASSWORD` | yes | — | MySQL password |
| `JWT_SECRET` | yes | — | HMAC secret for signing access/refresh JWTs; must be ≥32 bytes |
| `JWT_EXPIRATION` | no | `3600000` (1h, ms) | Access token lifetime |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:3000` | Comma-separated storefront origins |
| `ADMIN_USERNAME` | yes | — | Bootstrap admin username, created on first startup |
| `ADMIN_PASSWORD` | yes | — | Bootstrap admin password |
| `ADMIN_EMAIL` | yes | — | Bootstrap admin email |
| `LOGIN_RATE_LIMIT_CAPACITY` | no | `5` | Failed login attempts allowed per window, per IP and per attempted username |
| `LOGIN_RATE_LIMIT_WINDOW_SECONDS` | no | `60` | Rate-limit window length |
| `RAZORPAY_KEY_ID` | no | — | Razorpay public key; leaving unset disables online payment (COD still works) |
| `RAZORPAY_KEY_SECRET` | no | — | Razorpay secret key, used server-side to create orders |
| `RAZORPAY_WEBHOOK_SECRET` | no | — | Secret configured on the Razorpay webhook, used to verify inbound signatures |
| `CLOUDINARY_URL` | no | — | Cloudinary connection string (`cloudinary://<api_key>:<api_secret>@<cloud_name>`); leaving unset disables image uploads |
| `CLOUDINARY_FOLDER` | no | `nerya` | Base folder uploads nest under (category appended, e.g. `nerya/hero`) |

## Tests

```bash
./gradlew test
```

Integration tests boot the full app against a real MySQL 8 Testcontainer (Docker required) and run Flyway migrations for real, so schema drift is caught the same way production would hit it.

## Docker

```bash
docker build -t nerya-all-naturals .
docker run -p 8080:8080 --env-file .env nerya-all-naturals
```

The image exposes `GET /actuator/health` as its healthcheck.

## API surface

See **`API_REFERENCE.md`** for the full API reference (every endpoint, request/response JSON shapes, auth requirements) — the doc to hand to frontend engineers. `API_ENDPOINTS.md` / `API_SUMMARY.md` cover catalog/category endpoints in more narrative detail, and `project-docs/IMPLEMENTATION_TASK_PLAN.md` has the phase-by-phase build history.
