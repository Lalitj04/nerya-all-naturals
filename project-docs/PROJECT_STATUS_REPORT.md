# Nerya All Naturals — Full Project Report

> **Generated:** 2026-07-12 (verified directly against source code, git history, and config on this date)
> **Companions:** `CURRENT_IMPLEMENTATION_AND_VULNERABILITIES.md` (2026-07-08 snapshot), `ECOMMERCE_BACKEND_IMPLEMENTATION_PLAN.md` (phase-by-phase build plan)
> **Contents:** ① What the project is and contains today · ② Security vulnerabilities · ③ What must be added for a fully working e-commerce backend

---

## ⛔ URGENT — Act before anything else

**Live database credentials are in LOCAL git history (not yet on GitHub).**

- Commit `41a9319` ("added creds", 2026-02-04) hardcoded the Aiven MySQL host, port, username `avnadmin`, and password (redacted here — `AVNS_***`) into `application.yml`.
- **Verified 2026-07-14:** this commit exists only in local history; `origin/main` does **not** contain it, so the credential has **not** reached GitHub. Cleaning local history before the next push prevents the leak entirely.
- The current working tree no longer contains the password (defaults are empty env placeholders). The local `.env` file holds the real credentials (gitignored and untracked — that part is fine).

**Required actions, in order:**
1. **Before any push:** rewrite/clean local history so commit `41a9319`'s password never enters a pushed commit (or push a clean branch built on `origin/main`).
2. **Rotate the Aiven database password** (Aiven console → service → reset credentials) as defense-in-depth — the password has sat in plaintext locally and in `.env`; treat it as potentially compromised.
3. Never commit real credentials again; keep them only in `.env` / environment variables / a secrets manager.

---

## 1. What this project is

**Nerya All Naturals** is the backend REST API for an e-commerce platform selling natural/organic products. It is roughly **30–40% of a complete store backend**: authentication, user administration, product catalog, categories, and admin inventory exist; everything a shopper needs to actually buy something (registration, cart, checkout, orders, payments) does not exist yet.

| Item | Value |
|---|---|
| Language / Framework | Java 21, Spring Boot 3.3.4 |
| Build | Gradle (wrapper committed) |
| Database | MySQL (Aiven cloud in prod, local for dev) via Spring Data JPA / Hibernate |
| Schema management | Hibernate `ddl-auto: update` (Flyway on classpath but **zero migration scripts**) |
| Auth | Stateless JWT (JJWT 0.12.6), BCrypt password hashing |
| API docs | springdoc-openapi 2.6.0 — Swagger UI at `/swagger-ui`, now with Bearer-token auth support via new `OpenApiConfig` |
| Server port | 8081 (`application.yml`) — **Dockerfile still exposes/health-checks 8080** |
| Deployment | Multi-stage Dockerfile (JDK 21, non-root user) |
| Tests | **None** (test dependencies declared, `src/test` does not exist) |

### 1.1 Package structure (`com.nerya.neryaallnaturals`)

```
├── NeryaAllNaturalsApplication.java
├── annotation/    @AdminOnly, @CustomerOnly, @UserOnly, @AdminOrUser, @AdminOrCustomer, @Authenticated
├── config/        SecurityConfig, OpenApiConfig (new, uncommitted)
├── controller/    Auth, User, Product, Category, Inventory
├── dto/           Request/response DTOs (Auth, Login, User, Product, Category, Inventory, ProductImage)
├── entity/        BaseEntity, User, Customer, Address, Product, Category, ProductImage, ProductReview, Inventory
├── filter/        JwtAuthenticationFilter (Bearer header → SecurityContext)
├── repository/    Spring Data JPA repos for all entities
├── service/       Auth, User, Product, Category, Inventory
├── util/          JwtUtil
└── web/           HealthController
```

### 1.2 Data model

```
BaseEntity (id, createdAt, updatedAt)
User ──ElementCollection── roles (ROLE_USER | ROLE_ADMIN | ROLE_CUSTOMER)
Category ──1:N── Category (parent/subcategories)
   └──1:N── Product ──1:N── ProductImage
                    ──1:N── ProductReview (raw customerId, no FK association)
                    ──tags── product_tags
                    ──1:1── Inventory
Customer / Address entities exist but have NO controllers or services
```

Known modelling quirks:
- **Stock lives in two places** — `Product.quantity`/`inStock` and `Inventory.quantityOnHand` — with no synchronization in code.
- `Address` and `ProductReview` reference customers via raw `Long customerId` instead of JPA associations.
- Products soft-delete (`isActive=false`); inventory hard-deletes.

### 1.3 Implemented API surface

**Public (permitAll):** `POST /api/auth/login`, `GET /api/auth/validate?token=`, `/api/health`, `/api/ping`, `/actuator/health|info`, `/swagger-ui/**`, `/v3/api-docs/**`.
Note: `SecurityConfig` permits **all of `/api/auth/**`** — any future endpoint added under that prefix is automatically public.

**Authenticated (any valid JWT):** catalog reads — `GET /api/products`, `/api/products/{id}`, `/api/products/category/{categoryId}`, `GET /api/categories`, `/api/categories/{id}`, `/api/categories/parents`. (Docs describe these as "public", but `anyRequest().authenticated()` means a guest cannot browse — a design mismatch for a storefront.)

**Admin (`@AdminOnly`):** product CRUD (`/api/products/admin/**`), category create (`/api/categories/admin`), full inventory CRUD (`/api/inventory/admin/**`), user create/list/delete (`/api/users`).

**Admin or User (`@AdminOrUser`):** user get/update by id, username, or email — **with no ownership check** (see V3).

### 1.4 Working-tree changes not yet committed

- `application.yml` — credentials reverted to localhost defaults, port changed 8080 → 8081, Swagger options added.
- `config/OpenApiConfig.java` — new; adds JWT Bearer scheme to Swagger UI.
- `project-docs/` — this folder.

---

## 2. Vulnerabilities & misconfigurations

All findings below were re-verified against the code on 2026-07-12. File references point to the exact lines.

### 2.1 Critical

| ID | Finding | Evidence | Impact / fix |
|----|---------|----------|--------------|
| **V0** | **Live DB credentials in local git history** | Commit `41a9319` (local only; not on `origin/main` as of 2026-07-14) | Password sits in plaintext in local history and `.env`. Clean history before pushing so it never reaches GitHub; rotate the Aiven password as defense-in-depth (see banner above). |
| **V1** | **Hardcoded default JWT secret** | `JwtUtil.java:23` — `@Value("${jwt.secret:mySecretKeyForJWT...}")` | The signing secret is public in source. Since `jwt.secret` is set nowhere (not in yml, not in `.env`), **every deployment uses it** — anyone can forge an admin token offline. Fix: require `JWT_SECRET` from env, fail startup if missing. |
| **V2** | **Privilege escalation to admin by any user** | `UserController.java:163-165` — `PUT /api/users/{id}` is `@AdminOrUser` and blindly applies `userRequest.getRoles()` | Any `ROLE_USER` can send `"roles": ["ROLE_ADMIN"]` and promote themselves (or anyone). Fix: only admins may change roles; strip `roles` from self-service updates. |
| **V3** | **IDOR — any user can read/modify any other user** | `UserController.java:98-171, 204-237` — `@AdminOrUser` endpoints never compare the target to the authenticated principal | A `ROLE_USER` can `GET`/`PUT` any account by id/username/email (change their email, password, name). Combined with V2 = full account takeover. Fix: enforce "own record only" for non-admins. |

### 2.2 High / Medium

| ID | Finding | Evidence | Impact / fix |
|----|---------|----------|--------------|
| V4 | Broad `permitAll` on `/api/auth/**` | `SecurityConfig.java:33` | Any future endpoint under `/api/auth/` is silently public. List explicit paths instead. |
| V5 | JWT accepted as URL query parameter | `AuthController.java:75` — `GET /api/auth/validate?token=` | Tokens leak into access logs, proxies, browser history, Referer headers. Validate from the `Authorization` header instead. |
| V6 | No login rate limiting / lockout | `AuthService` / no filter | Unlimited password brute-force against `/api/auth/login`. Add Bucket4j or similar. |
| V7 | `ddl-auto: update` in all environments + Flyway unused | `application.yml:11`; no `db/migration` folder | Hibernate mutates the production schema on startup; no controlled migrations. Write a Flyway `V1__baseline.sql`, set `ddl-auto: validate` outside local dev. |
| V8 | Swagger UI fully public | `SecurityConfig.java:32` | Complete API discovery (all admin routes) on any deployed instance. Disable or protect in prod (profile flag). |
| V9 | Docker port mismatch | `Dockerfile:43,47` (8080) vs `application.yml:25` (8081) | Container health checks fail / service unreachable unless overridden. Align to one port. |
| V10 | JWT auth failures swallowed silently | `JwtAuthenticationFilter.java:55-57` | Invalid/expired tokens fall through to a generic 403 with no distinction; harder to debug and no audit trail. |

### 2.3 Low / hygiene

| ID | Finding | Notes |
|----|---------|-------|
| V11 | No CORS configuration | A browser-based storefront on another origin cannot call this API; when added, avoid `*` with credentials. |
| V12 | No global exception handler | No `@ControllerAdvice`; controllers return raw strings; risk of leaking internals and inconsistent error shapes. |
| V13 | Dual stock model | `Product.quantity` vs `Inventory.quantityOnHand` can silently diverge → overselling or false out-of-stock. |
| V14 | `UserController` bypasses the service layer | Talks to `UserRepository`/`PasswordEncoder` directly; inconsistent with every other domain. |
| V15 | No automated tests at all | `src/test` doesn't exist. Security regressions (V2/V3 fixes) will go undetected. |
| V16 | CSRF disabled | Fine for a pure Bearer-token API; becomes dangerous if cookie auth is ever added. |
| V17 | Admin bootstrap chicken-and-egg | User creation is `@AdminOnly` and there's no registration — first admin must be seeded manually; document/script this securely. |
| V18 | Error messages echo user input | e.g. "User not found with email: {email}" enables account enumeration on user-lookup endpoints. |

### 2.4 Fix priority

1. **V0 — rotate the Aiven DB password (today).**
2. V1 — require `JWT_SECRET` from env, no default.
3. V2 + V3 — role-change restriction + ownership checks (one PR, plus tests).
4. V5, V6, V8 — token-in-URL, rate limiting, Swagger lockdown.
5. V7, V9 — Flyway baseline, Docker port alignment.
6. Everything else opportunistically as features are built.

---

## 3. What's missing for a fully working e-commerce backend

Ordered roughly as they should be built. The detailed task breakdown (with table schemas, endpoint specs, and the Google Drive media design) lives in `ECOMMERCE_BACKEND_IMPLEMENTATION_PLAN.md` — this is the summary checklist.

### 3.1 Foundation (do first — Phase P0)

- [ ] All security fixes from §2.4 above
- [ ] **Public catalog** — `permitAll` for `GET /api/products/**` and `GET /api/categories/**` (guests must be able to browse)
- [ ] **Flyway migrations** — baseline current schema; stop relying on `ddl-auto: update`
- [ ] **CORS config** — configurable allowed origins for the storefront UI
- [ ] **Global exception handling** — `@ControllerAdvice` with a consistent `{code, message}` error body
- [ ] **Spring profiles** — `local` vs `prod` config (DB, Swagger toggle, JWT)

### 3.2 Customer identity (Phase P2)

- [ ] `POST /api/auth/register` — public self-signup creating `User` (ROLE_CUSTOMER) + linked `Customer`
- [ ] `GET/PUT /api/customers/me` — own profile
- [ ] Address CRUD — `/api/customers/me/addresses` (fix `Address`↔`Customer` JPA associations first)
- [ ] Refresh tokens / logout / token revocation
- [ ] Secure first-admin seeding process

### 3.3 Catalog completion (Phase P1/P3)

- [ ] Media handling — planned design: images in Google Drive, metadata in a `media_assets` MySQL table, public fetch APIs by category/product (full spec in the implementation plan)
- [ ] Product search & filtering (`q`, price range, category, in-stock) + pagination
- [ ] Category update/delete (only create exists today)
- [ ] **Unify stock** — make `Inventory` the single source of truth; auto-create inventory rows on product creation

### 3.4 Commerce core (Phases P4–P5) — the biggest gap

- [ ] **Cart** — `carts` + `cart_items` tables; add/update/remove/clear APIs; stock validation on add
- [ ] **Checkout** — transactional: validate cart → reserve inventory → create order → clear cart
- [ ] **Orders** — `orders` + `order_items` with price/address snapshots; status flow `PENDING → CONFIRMED → PACKED → SHIPPED → DELIVERED / CANCELLED`
- [ ] Customer order history + admin order management APIs
- [ ] Inventory reservation/release logic (prevent overselling)

### 3.5 Payments & post-purchase (Phases P6–P7)

- [ ] Payment provider integration (Razorpay for India / Stripe) — payment intent + signed webhook → mark order paid; or COD as MVP
- [ ] Product reviews APIs (entity exists, no endpoints) — optionally restricted to customers with a delivered order
- [ ] Transactional email (order confirmation, etc.)

### 3.6 Operations & quality (Phase P8)

- [ ] Integration tests — priority on authorization (IDOR, role escalation), checkout stock math, cart flows
- [ ] Login rate limiting, Swagger locked down in prod
- [ ] README documenting all env vars (`JWT_SECRET`, `DB_*`, CORS origins, Drive folder ids)
- [ ] Seed/demo data for development

### 3.7 MVP definition of done

The backend is a functional MVP storefront when: security P0 fixes are merged · a guest can browse products with images · a customer can register, fill a cart, and check out · an admin can manage orders and inventory without stock drift · Flyway owns the schema · at least one payment path (COD or gateway sandbox) completes.

**This MVP definition of done is now met** (see §4 below).

---

## 4. Update — 2026-07-24: Phases P5–P8 complete

All phases through P8 in `IMPLEMENTATION_TASK_PLAN.md` are implemented on branch `phase6`:

- **P5 — Orders & checkout** (T45–T51): `orders`/`order_items` (`V8__orders.sql`), transactional checkout with row-locked inventory reservation, idempotent replay via `Idempotency-Key`, customer + admin order APIs, status state machine, stock lifecycle on ship/cancel.
- **P6 — Payments** (T52–T55): `payments` (`V9__payments.sql`), COD (`PaymentProvider.COD`, marks order `COD`/`CONFIRMED` immediately) and Razorpay sandbox (`PaymentProvider.RAZORPAY` — order creation via REST, `POST /api/payments/webhook` with HMAC-SHA256 signature verification and duplicate-delivery idempotency).
- **P7 — Reviews** (T56–T57): `ProductReview.customerId` replaced with a proper `Customer` FK + one-review-per-customer-per-product unique constraint (`V10__product_reviews_fk.sql`); public paged reads, customer-scoped create/update/delete, admin moderation delete; `verifiedPurchase` auto-computed from delivered-order history (never client-supplied); `Product.averageRating`/`totalReviews` recomputed on every write.
- **P8 — Hardening** (T58–T60): login brute-force throttling (`LoginRateLimitFilter`, Bucket4j, 5 failed attempts/min per IP and per attempted username, configurable via `rate-limit.login.*` / `LOGIN_RATE_LIMIT_CAPACITY` / `LOGIN_RATE_LIMIT_WINDOW_SECONDS`); regression suite covering checkout races, cart, identity/IDOR/role-escalation, payments (COD, webhook signature + idempotency), reviews, and a forged-JWT/rate-limit security suite — 49 tests, `./gradlew test` green against a real MySQL Testcontainer.

New environment variables: `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`, `LOGIN_RATE_LIMIT_CAPACITY` (default 5), `LOGIN_RATE_LIMIT_WINDOW_SECONDS` (default 60). Razorpay keys are optional — leaving them unset disables the gateway path while COD continues to work; the online-payment endpoint then returns 409 instead of 500.

Not yet done: real Razorpay sandbox credentials haven't been exercised end-to-end (only signature verification and the not-configured path are covered by tests); transactional email is still unimplemented (§3.5).

---

*End of report.*
