# Nerya All Naturals — Full Backend Implementation Task Plan

> **Generated:** 2026-07-13 · based on the verified state in `PROJECT_STATUS_REPORT.md`
> **Supersedes:** `ECOMMERCE_BACKEND_IMPLEMENTATION_PLAN.md` (2026-07-08) — same direction, now broken into concrete, executable tasks with files and acceptance criteria.
> **Goal:** From the current skeleton (auth, users, catalog, admin inventory) to a fully functional e-commerce backend, including the custom feature: **images stored in Google Drive, paths/metadata in a MySQL (RDS/Aiven) table, and public APIs for the UI to fetch images by category.**

---

## How to use this plan

- Tasks are numbered **T1…T60** and grouped into phases. Each task is small enough to implement and verify in one sitting.
- Phases must be done in order (later phases build on earlier tables/APIs), but tasks *within* a phase are mostly parallelizable.
- Each task lists the **files** it touches and an **acceptance check**.
- Track progress by checking boxes here, and update `PROJECT_STATUS_REPORT.md` after each phase.

**Phase overview:**

| Phase | Name | Tasks | Outcome |
|-------|------|-------|---------|
| P0 | Security & foundation | T1–T14 | Safe auth, Flyway, CORS, errors, public catalog |
| P1 | **Google Drive + MySQL media** (your custom feature) | T15–T26 | Admin uploads to Drive; UI fetches images by category |
| P2 | Customer registration & profile | T27–T33 | Shoppers can sign up and manage addresses |
| P3 | Catalog completion | T34–T39 | Search, pagination, stock unified, images on products |
| P4 | Cart | T40–T44 | Customers maintain a cart |
| P5 | Orders & checkout | T45–T51 | Cart → order with inventory reservation |
| P6 | Payments | T52–T55 | COD and/or gateway sandbox marks orders paid |
| P7 | Reviews | T56–T57 | Customers review purchased products |
| P8 | Hardening, tests, ops | T58–T60 | Rate limiting, tests, docs |

---

## Phase P0 — Security & foundation (do first, nothing else before this)

Fixes the critical findings V0–V3 and the structural gaps (Flyway, CORS, errors, public catalog) so every later feature is built on solid ground.

### T1 — Rotate the leaked Aiven database password ⛔ (ops, not code)
Commit `41a9319` pushed the live DB password to GitHub. Rotate it in the Aiven console, update local `.env`, verify the app connects.
**Acceptance:** old password no longer works; app boots against DB with new password.

### T2 — Require `JWT_SECRET` from environment (fix V1)
- `src/main/java/.../util/JwtUtil.java` — change `@Value("${jwt.secret:...default...}")` to `@Value("${jwt.secret}")`; add an `@PostConstruct` check that the secret is ≥ 32 bytes, throw `IllegalStateException` otherwise.
- `application.yml` — add `jwt.secret: ${JWT_SECRET}` and `jwt.expiration: ${JWT_EXPIRATION:86400000}`.
- `.env` — add a generated `JWT_SECRET` (e.g. `openssl rand -base64 48`).
**Acceptance:** app fails fast without `JWT_SECRET`; old hardcoded string rejected everywhere.

### T3 — Block role self-escalation (fix V2)
- New `dto/UserProfileUpdateRequest.java` **without** a `roles` field for self-service updates.
- `UserController.updateUser` — apply roles only when the caller has `ROLE_ADMIN` (check via `Authentication`); otherwise ignore/400 if roles present.
**Acceptance:** a `ROLE_USER` sending `"roles":["ROLE_ADMIN"]` gets no role change; admin still can change roles.

### T4 — Enforce ownership on user endpoints (fix V3)
- `UserController` (`GET/PUT /api/users/{id}`, `/username/{u}`, `/email/{e}`): if caller is not admin, the target user must equal the authenticated principal (compare username from `SecurityContext`), else 403.
**Acceptance:** user A gets 403 fetching/updating user B; admin unaffected.

### T5 — Route `UserController` through `UserService` (fix V14)
Move repository + password-encoder logic from `UserController` into `UserService` methods (`createUser`, `updateUser`, `deleteUser`, finders). Controller keeps only HTTP concerns.
**Acceptance:** controller has no `UserRepository`/`PasswordEncoder` field; behavior unchanged.

### T6 — Tighten `/api/auth/**` permit list (fix V4) and drop token-in-query (fix V5)
- `SecurityConfig` — replace `.requestMatchers("/api/auth/**")` with explicit `POST /api/auth/login`, `POST /api/auth/register` (coming in P2), `GET /api/auth/validate`.
- `AuthController.validateToken` — read the token from the `Authorization: Bearer` header; remove/deprecate `?token=` param.
**Acceptance:** `/api/auth/anything-else` returns 401; validate works via header only.

### T7 — Make catalog reads public (fix design mismatch V-catalog)
- `SecurityConfig` — `permitAll` for `GET /api/products`, `GET /api/products/{id}`, `GET /api/products/category/**`, `GET /api/categories/**` (matchers must NOT cover `/admin/` paths — keep those on `@AdminOnly`).
**Acceptance:** `curl` with no token can list products/categories; admin routes still 401/403.

### T8 — Global exception handling (fix V12, V18)
- New `exception/` package: `GlobalExceptionHandler` (`@RestControllerAdvice`), `ResourceNotFoundException`, `ConflictException`, standard body `{ "code", "message", "timestamp", "path" }`.
- Replace raw-string error responses in controllers; make not-found messages generic (no email/username echo — fixes enumeration).
**Acceptance:** unknown id returns consistent JSON 404; validation errors return field-level 400 JSON.

### T9 — CORS configuration (fix V11)
- `config/CorsConfig.java` — `CorsConfigurationSource` bean reading `cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}`; wire into `SecurityConfig` via `.cors(...)`.
**Acceptance:** preflight `OPTIONS` from the configured origin returns proper CORS headers.

### T10 — Flyway baseline migration (fix V7)
- Create `src/main/resources/db/migration/V1__baseline.sql` — full DDL for existing tables: `users`, `user_roles`, `customers`, `addresses`, `categories`, `products`, `product_images`, `product_reviews`, `product_tags`, `inventory` (capture from a fresh Hibernate-generated schema).
- `application.yml` — `spring.flyway.enabled: true`, `baseline-on-migrate: true`; set `ddl-auto: validate`.
**Acceptance:** fresh DB boots via Flyway only; existing DB baselines cleanly; `ddl-auto` no longer mutates schema.

### T11 — Spring profiles
- `application.yml` (shared) + `application-local.yml` (localhost DB, Swagger on) + `application-prod.yml` (env-only DB, Swagger off, `ddl-auto: validate`). Note: `application-local/prod.yml` are already gitignored — commit templates as `application-local.yml.example`.
**Acceptance:** `SPRING_PROFILES_ACTIVE=local` and `=prod` both boot with correct settings.

### T12 — Swagger lockdown for prod (fix V8)
- In `application-prod.yml`: `springdoc.api-docs.enabled: false`, `springdoc.swagger-ui.enabled: false` (or gate behind admin auth if you prefer).
**Acceptance:** prod profile serves 404 on `/swagger-ui`.

### T13 — Align Docker port (fix V9)
- `Dockerfile` — `EXPOSE 8081` and health-check `http://localhost:8081/actuator/health` (or set `SERVER_PORT=8080` env; pick one and be consistent).
**Acceptance:** `docker build && docker run` container reports healthy.

### T14 — Admin bootstrap seeding (fix V17)
- Flyway `V2__seed_admin.sql` inserting one admin with a BCrypt hash sourced from an env-substituted placeholder, **or** a `CommandLineRunner` that creates the admin from `ADMIN_USERNAME`/`ADMIN_PASSWORD` env vars if no admin exists. Prefer the runner (no hash in SQL).
**Acceptance:** fresh DB + env vars → admin can log in; runner is a no-op when an admin exists.

**P0 exit criteria:** all critical vulns closed, guest can browse catalog, Flyway owns schema, UI origin can call the API, errors are consistent JSON.

---

## Phase P1 — Google Drive images + MySQL `media_assets` (your custom feature)

**Flow you asked for:**
```
Admin uploads image ──► Google Drive folder (by category: HERO / BANNER / PRODUCT / CATEGORY)
                              │ file id + share link
                              ▼
                MySQL table media_assets (path, category, URLs, product link)
                              │
                              ▼
        Public GET APIs ──► UI fetches metadata by category ──► browser renders image URL
```

### T15 — Google Cloud setup (ops, ~30 min, no code)
1. Create a GCP project, enable **Google Drive API**.
2. Create a **service account**; download the JSON key. Never commit it — reference via `GOOGLE_APPLICATION_CREDENTIALS` (file path) or a base64 env var.
3. In your Google Drive, create folders: `nerya-media/hero`, `/banner`, `/product`, `/category`, `/gallery`.
4. Share `nerya-media` with the service-account email as **Editor**.
5. Record each folder's Drive folder ID (from its URL).
**Acceptance:** you have a key file + 5 folder IDs.

### T16 — Gradle dependencies
```groovy
implementation 'com.google.api-client:google-api-client:2.6.0'
implementation 'com.google.apis:google-api-services-drive:v3-rev20240628-2.0.0'
implementation 'com.google.auth:google-auth-library-oauth2-http:1.24.1'
```
**Acceptance:** project compiles.

### T17 — Config keys
`application.yml`:
```yaml
media:
  google:
    credentials-path: ${GOOGLE_DRIVE_CREDENTIALS:}
    folders:
      HERO: ${DRIVE_FOLDER_HERO:}
      BANNER: ${DRIVE_FOLDER_BANNER:}
      PRODUCT: ${DRIVE_FOLDER_PRODUCT:}
      CATEGORY: ${DRIVE_FOLDER_CATEGORY:}
      GALLERY: ${DRIVE_FOLDER_GALLERY:}
```
Add all values to `.env`.
**Acceptance:** properties bind into a `@ConfigurationProperties(prefix="media.google")` class.

### T18 — Flyway migration `V3__media_assets.sql`
```sql
CREATE TABLE media_assets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  drive_file_id VARCHAR(128) NOT NULL UNIQUE,
  file_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(100),
  category VARCHAR(50) NOT NULL,          -- HERO | BANNER | PRODUCT | CATEGORY | GALLERY
  folder_path VARCHAR(512),
  public_url VARCHAR(1024) NOT NULL,      -- canonical URL the UI renders
  web_view_link VARCHAR(1024),
  thumbnail_link VARCHAR(1024),
  alt_text VARCHAR(255),
  sort_order INT DEFAULT 0,
  product_id BIGINT NULL,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_media_product FOREIGN KEY (product_id) REFERENCES products(id),
  INDEX idx_media_category (category, is_active, sort_order),
  INDEX idx_media_product (product_id)
);
```
**Acceptance:** migration applies; table exists.

### T19 — Entity + repository
- `entity/MediaAsset.java` (extends `BaseEntity`), `enum MediaCategory { HERO, BANNER, PRODUCT, CATEGORY, GALLERY }`.
- `repository/MediaAssetRepository.java` — `findByCategoryAndIsActiveTrueOrderBySortOrderAsc`, `findByProductIdAndIsActiveTrue`, `findByDriveFileId`.
**Acceptance:** app boots with `ddl-auto: validate` (entity matches migration).

### T20 — `GoogleDriveConfig` + `GoogleDriveService`
- `config/GoogleDriveConfig.java` — build a `Drive` bean from the service-account credentials (scope `DriveScopes.DRIVE`). If credentials path is empty, skip bean creation (`@ConditionalOnProperty`) so local dev without Drive still boots.
- `service/GoogleDriveService.java`:
  - `upload(MultipartFile file, String folderId)` → returns Drive `File` (id, links)
  - `makePublicReader(String fileId)` → permission `type=anyone, role=reader`
  - `delete(String fileId)`, `getMetadata(String fileId)`
- Public URL convention: `https://drive.google.com/uc?export=view&id=<FILE_ID>` stored in `public_url`.
**Acceptance:** unit-level smoke: uploading a test file returns an id and the URL renders in a browser.

### T21 — `MediaService`
Orchestrates: resolve folder ID from category → upload → make public → build `public_url` → save `MediaAsset` row. Also `update` (altText, sortOrder, category, isActive), `softDelete` (isActive=false; optionally Drive delete on hard purge), `registerExisting(driveFileId, category, …)` for files already in Drive.
**Acceptance:** one service call ends with a Drive file + a DB row.

### T22 — Admin upload API
`controller/MediaController.java`:
- `POST /api/media/admin/upload` — `@AdminOnly`, multipart: `file` (required), `category` (required), `altText`, `sortOrder`, `productId` (optional). Validates mime type (`image/jpeg|png|webp|gif`) and size (e.g. ≤ 5 MB via `spring.servlet.multipart.max-file-size`). Returns saved metadata DTO.
**Acceptance:** curl multipart upload as admin → 201 with `publicUrl`; non-admin → 403.

### T23 — Admin register/update/delete APIs
- `POST /api/media/admin/register` — body `{driveFileId, category, altText, sortOrder, productId}` for images you placed in Drive manually.
- `PUT /api/media/admin/{id}` — update metadata/sort/category/isActive.
- `DELETE /api/media/admin/{id}` — soft delete (set inactive); `?hard=true` also deletes from Drive.
**Acceptance:** all three verbs work as admin, 403 otherwise.

### T24 — Public fetch APIs (what the UI calls)
Same controller, `permitAll` in `SecurityConfig`:

| Method | Path | Behavior |
|--------|------|----------|
| GET | `/api/media` | active assets; optional `?category=HERO&productId=7` filters |
| GET | `/api/media/category/{category}` | active in category, ordered by `sort_order` |
| GET | `/api/media/product/{productId}` | product gallery |
| GET | `/api/media/{id}` | single asset |

Response DTO:
```json
{ "id": 12, "category": "HERO", "publicUrl": "https://drive.google.com/uc?export=view&id=FILE_ID",
  "altText": "Nerya hero banner", "sortOrder": 1, "productId": null }
```
**Acceptance:** no-token GET returns JSON; UI can render `publicUrl` in an `<img>` tag.

### T25 — Wire media into products
- `ProductResponse` gains `images: [MediaAssetResponse]` populated from `media_assets.product_id` (fall back to legacy `ProductImage` rows while both exist).
- Product create/update optionally accepts `mediaAssetIds` to attach existing assets.
**Acceptance:** `GET /api/products/{id}` returns Drive-backed image URLs.

### T26 — Swagger + docs for media
Tag the media endpoints in OpenAPI; document env vars and folder setup in README/`project-docs`.
**Acceptance:** media endpoints visible and testable in Swagger UI with bearer token.

**P1 exit criteria:** you upload an image via API → it lands in the right Drive folder → a row exists in MySQL → the UI (or curl) fetches `GET /api/media/category/HERO` without auth and renders the image.

> **Note:** Google Drive is not a CDN — fine for launch traffic, but plan an S3/CloudFront migration path if the site grows. The `media_assets` abstraction makes that swap invisible to the UI.

---

## Phase P2 — Customer registration & profile

### T27 — Registration API
- `POST /api/auth/register` (public): `{username, email, password, firstName, lastName, phoneNumber}` → creates `User` with `ROLE_CUSTOMER` + linked `Customer` row in one transaction. BCrypt password; unique username/email checks.
**Acceptance:** register → login → token contains `ROLE_CUSTOMER`.

### T28 — Extend `AuthResponse`
Add `roles` and `customerId` so the UI can route admin vs shopper.
**Acceptance:** login response includes roles.

### T29 — Fix JPA associations
`Address` gets `@ManyToOne Customer customer` (replace raw `customerId`); `Customer` gets `@OneToMany addresses` and `@OneToOne User`. Flyway `V4__customer_fks.sql` adds FKs.
**Acceptance:** schema validates; cascade delete rules decided (addresses die with customer).

### T30 — Customer profile APIs
- `GET /api/customers/me`, `PUT /api/customers/me` — resolve customer from the authenticated principal, never from a path id. `@CustomerOnly` (or authenticated + role check).
**Acceptance:** customer A can never read customer B.

### T31 — Address CRUD
`GET/POST/PUT/DELETE /api/customers/me/addresses[/{addressId}]` — ownership enforced (address must belong to caller's customer). Support a `isDefault` flag.
**Acceptance:** full CRUD works; cross-customer address id returns 404/403.

### T32 — Refresh token / logout (recommended, can defer)
`refresh_tokens` table (Flyway `V5`), `POST /api/auth/refresh`, `POST /api/auth/logout` revoking the refresh token. Shorten access-token TTL to ~1h.
**Acceptance:** expired access token can be renewed; logout invalidates refresh.

### T33 — Integration tests for identity
Testcontainers MySQL: register/login flow, ownership checks (T4, T30, T31), role-escalation regression (T3).
**Acceptance:** `./gradlew test` green with these cases.

---

## Phase P3 — Catalog completion

### T34 — Category update/delete
`PUT /api/categories/admin/{id}`, `DELETE /api/categories/admin/{id}` (soft delete; block/deactivate children and detach products or refuse when products exist — pick refuse for safety).
**Acceptance:** admin can rename/deactivate categories.

### T35 — Product search, filter, pagination
`GET /api/products?q=&categoryId=&minPrice=&maxPrice=&inStock=&page=&size=&sort=` using Spring Data `Specification` + `Pageable` (default size 20, max 100). Response becomes a page object `{content, page, size, totalElements}`.
**Acceptance:** combined filters return correct results; page never exceeds max.

### T36 — Unify stock (fix V13)
`Inventory` becomes the single source of truth. Remove writes to `Product.quantity`; `Product.inStock` computed from inventory (`quantityOnHand - quantityReserved > 0`) — either on read or synced by `InventoryService` on every change.
**Acceptance:** changing inventory immediately changes product `inStock` in the public API.

### T37 — Auto-create inventory on product create
`ProductService.createProduct` creates an `Inventory` row (qty from request or 0).
**Acceptance:** every product has exactly one inventory row.

### T38 — Category images
Optional `category_id` column on `media_assets` (Flyway `V6`) + `GET /api/media/category-entity/{categoryId}`; `CategoryResponse` includes thumbnail URL.
**Acceptance:** storefront category tiles have images.

### T39 — Catalog tests
Search/filter/pagination + public-access (no token) tests.

---

## Phase P4 — Cart

### T40 — Schema: Flyway `V7__cart.sql`
`carts` (id, customer_id UNIQUE, created/updated) · `cart_items` (id, cart_id FK, product_id FK, quantity, UNIQUE(cart_id, product_id)).

### T41 — Entity/repo/service
`Cart`, `CartItem`, `CartRepository`, `CartService` (get-or-create by customer, add, update qty, remove, clear, compute totals from **current** selling price).

### T42 — Cart APIs (`@CustomerOnly`, principal-scoped)
| Method | Path | Behavior |
|--------|------|----------|
| GET | `/api/cart` | full cart with line totals + grand total |
| POST | `/api/cart/items` | `{productId, quantity}` add/merge |
| PUT | `/api/cart/items/{productId}` | set quantity (0 = remove) |
| DELETE | `/api/cart/items/{productId}` | remove line |
| DELETE | `/api/cart` | clear |

### T43 — Stock validation on add/update
Reject quantity exceeding `quantityOnHand - quantityReserved` with a clear error code (`INSUFFICIENT_STOCK`).

### T44 — Cart tests
Add/update/remove/clear, stock-limit rejection, cross-customer isolation.

**P4 exit criteria:** customer builds a cart, totals correct, can't exceed stock.

---

## Phase P5 — Orders & checkout

### T45 — Schema: Flyway `V8__orders.sql`
- `orders`: id, order_number (unique, e.g. `NRY-2026-000123`), customer_id, status, payment_status, subtotal, shipping_fee, discount, total, shipping snapshot columns (name, phone, line1, line2, city, state, pincode), placed_at, timestamps.
- `order_items`: order_id, product_id, sku, product_name, unit_price, quantity, line_total (all **snapshotted** — later price/product edits must not rewrite history).
- Status enums: `PENDING → CONFIRMED → PACKED → SHIPPED → DELIVERED | CANCELLED`; payment: `UNPAID | PAID | REFUNDED | COD`.

### T46 — Checkout service (the critical transaction)
`OrderService.checkout(customerId, addressId, idempotencyKey)`:
1. Load cart; reject if empty.
2. For each line, lock inventory (`SELECT … FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)`), verify available ≥ qty, increment `quantityReserved`.
3. Snapshot prices + address onto order/items; create order `PENDING`.
4. Clear cart. All in one `@Transactional`.
**Acceptance:** two concurrent checkouts for the last unit → exactly one succeeds.

### T47 — Customer order APIs
`POST /api/orders/checkout` (`{addressId}` + optional `Idempotency-Key` header) · `GET /api/orders/me` (paged) · `GET /api/orders/me/{orderNumber}` · `POST /api/orders/me/{orderNumber}/cancel` (only while `PENDING/CONFIRMED`; releases reservation).

### T48 — Admin order APIs
`GET /api/orders/admin?status=&customerId=&from=&to=` (paged) · `GET /api/orders/admin/{orderNumber}` · `PUT /api/orders/admin/{orderNumber}/status` with legal-transition validation.

### T49 — Inventory lifecycle on status change
`SHIPPED`: reserved → sold (`quantityReserved -= q`, `quantitySold += q`, `quantityOnHand -= q`). `CANCELLED`: release reservation. Centralize in `InventoryService`.

### T50 — Idempotent checkout
Store `idempotency_key` on orders (unique per customer); replaying the same key returns the existing order instead of double-charging stock.

### T51 — Order tests
Happy path, concurrent-stock race, cancel-releases-stock, snapshot immutability, status-transition rules.

**P5 exit criteria:** full guest→browse, customer→cart→checkout→order-history loop works; admin advances statuses; stock never drifts.

---

## Phase P6 — Payments (MVP)

### T52 — Decide provider
**Razorpay** if selling in India (likely for this brand), Stripe otherwise. Start with **COD + one gateway sandbox**.

### T53 — Schema: Flyway `V9__payments.sql`
`payments`: id, order_id FK, provider (`RAZORPAY|STRIPE|COD`), provider_order_id, provider_payment_id, amount, currency, status (`CREATED|SUCCESS|FAILED|REFUNDED`), created/updated.

### T54 — Payment APIs
- `POST /api/payments/create` (`@CustomerOnly`) — for an unpaid own-order: COD → mark order `COD`/`CONFIRMED`; gateway → create provider order, return checkout params for the UI widget.
- `POST /api/payments/webhook` (public path, **signature-verified** with provider secret) — on success set payment `SUCCESS` + order `PAID/CONFIRMED`; on failure record `FAILED`.
**Acceptance:** sandbox payment flips order to paid; tampered webhook signature is rejected.

### T55 — Payment tests
Webhook signature verification, COD flow, double-webhook idempotency.

---

## Phase P7 — Reviews

### T56 — Review APIs
Fix `ProductReview` associations (proper `Customer` + `Product` FKs, Flyway `V10`). Endpoints: public `GET /api/products/{id}/reviews` (paged) · `POST/PUT/DELETE /api/products/{id}/reviews/me` (`@CustomerOnly`, one per customer per product) · `DELETE /api/reviews/admin/{id}` (moderation). Recompute `Product.averageRating`/`reviewCount` on write.

### T57 — Verified-purchase rule (optional)
Only customers with a `DELIVERED` order containing the product may review; mark `verifiedPurchase=true`.

---

## Phase P8 — Hardening, tests, ops

### T58 — Login rate limiting (fix V6)
Bucket4j filter on `POST /api/auth/login` (e.g. 5 attempts/min per IP + per username) returning 429.

### T59 — Full regression test suite
Testcontainers-based end-to-end: register → browse → cart → checkout → pay(COD) → admin ship → review. Plus the security regressions (IDOR, escalation, forged JWT rejected).

### T60 — Documentation & deploy polish
README with every env var (`JWT_SECRET`, `DB_*`, `GOOGLE_DRIVE_CREDENTIALS`, `DRIVE_FOLDER_*`, `CORS_ALLOWED_ORIGINS`, `ADMIN_*`, payment keys) · seed script with demo products + Drive images (dev only) · update `PROJECT_STATUS_REPORT.md` · verify Docker image healthy end-to-end.

---

## Phase P9 — Blog / CMS (admin-authored blog posts)

Admin writes and publishes blog posts with images; the storefront lists/reads only published ones. Images ride the existing Google Drive media pipeline from Phase P1 rather than a new upload path.

### T61 — Schema: Flyway `V11__blogs.sql`
- `blogs`: id, title, slug (unique, URL-safe), excerpt (short teaser, nullable), content (`TEXT`/`LONGTEXT`, HTML or Markdown produced by the admin's editor — sanitize on render in the frontend, not here), status (`DRAFT|PUBLISHED`), author_admin_id (FK → `users`), published_at (nullable — set on first publish), created_at/updated_at.
- Alter `media_assets`: extend the `category` enum with `BLOG`, add nullable `blog_id BIGINT` + `fk_media_assets_blog FOREIGN KEY … REFERENCES blogs(id)`, following the exact pattern `linkedCategory`/`category_id` already uses for category tile images (`entity/MediaAsset.java`, `V2__media_assets.sql`).
- Index `idx_blogs_status_published_at (status, published_at)` for the public listing query, and `uk_blogs_slug UNIQUE (slug)`.

### T62 — Entity + repository
`Blog extends BaseEntity` (Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, matching every other entity) with a `@OneToMany(mappedBy = "blog")` back-reference from `MediaAsset` (mirrors `Product.images`). Add `MediaAsset.MediaCategory.BLOG` and a `linkedBlog` FK field next to the existing `linkedCategory` one. `BlogRepository` — `findBySlug`, `findByStatus(Pageable)` for the public list, `existsBySlug` for uniqueness checks on create/rename.

### T63 — Slug generation
Slugify the title (lowercase, hyphenated, strip non-alphanumerics) on create; if it collides, append `-2`, `-3`, etc. Slug is immutable after publish (breaks shared links otherwise) — allow it to be edited only while `DRAFT`.

### T64 — Admin blog APIs
- `POST /api/blogs/admin` (`@AdminOnly`) — create as `DRAFT`.
- `PUT /api/blogs/admin/{id}` (`@AdminOnly`) — edit title/excerpt/content; slug editable only in `DRAFT` (T63).
- `POST /api/blogs/admin/{id}/publish` / `POST /api/blogs/admin/{id}/unpublish` (`@AdminOnly`) — flips `status` and stamps `published_at` on first publish only (never overwritten by re-publish).
- `DELETE /api/blogs/admin/{id}` (`@AdminOnly`).
- `GET /api/blogs/admin` / `GET /api/blogs/admin/{id}` (`@AdminOnly`) — sees drafts too, unlike the public reads.

### T65 — Blog image APIs
Reuse `MediaService`/`GoogleDriveService` from Phase P1 exactly as products do: `POST /api/media/admin/upload` with `category=BLOG` and a `blogId` to link a cover image or an inline gallery image to a post, `sortOrder` controlling cover-vs-gallery position (`sortOrder = 0` convention = cover, matching how `ProductImage`/media ordering already works). No new upload endpoint needed — only the `BLOG` enum value and `blogId` linkage from T61/T62.

### T66 — Public blog APIs
`GET /api/blogs` (paged, `status = PUBLISHED` only, newest-`published_at`-first) · `GET /api/blogs/{slug}` (404 if `DRAFT` or missing — never leak unpublished content by slug guessing). Both `permitAll`, added to `SecurityConfig` alongside the other public catalog reads. Response includes the resolved cover image URL + gallery image list via the linked `MediaAsset`s.

### T67 — Blog tests
Admin create → edit → publish → appears in public list/by-slug; unpublish removes it from public reads but keeps it visible to admin; slug uniqueness/collision suffixing; draft is 404 publicly even with the correct slug; non-admin write attempts rejected (403).

**Acceptance:** an admin can write a post with a cover image and inline images, save it as a draft, come back and publish it, and it then appears on the public site with working image URLs; unpublishing hides it again without deleting it.

---

## Final target API surface (condensed)

| Audience | Endpoints |
|----------|-----------|
| **Public** | `POST /api/auth/login`, `POST /api/auth/register` · `GET /api/products…` (search/filter/paged) · `GET /api/categories…` · `GET /api/media…` (by category/product) · `GET /api/products/{id}/reviews` · `GET /api/blogs`, `GET /api/blogs/{slug}` · health |
| **Customer** | `/api/customers/me` + addresses · `/api/cart…` · `POST /api/orders/checkout`, `/api/orders/me…` · `/api/payments/create` · review write |
| **Admin** | product/category/inventory/user admin (hardened) · `/api/media/admin/upload|register|{id}` · `/api/orders/admin…` · review moderation · `/api/blogs/admin…` (CRUD + publish/unpublish) |

## Environment variables (final set)

```
DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD        # rotated!
JWT_SECRET, JWT_EXPIRATION
CORS_ALLOWED_ORIGINS
GOOGLE_DRIVE_CREDENTIALS                                    # path to service-account JSON
DRIVE_FOLDER_HERO, DRIVE_FOLDER_BANNER, DRIVE_FOLDER_PRODUCT,
DRIVE_FOLDER_CATEGORY, DRIVE_FOLDER_GALLERY, DRIVE_FOLDER_BLOG   # BLOG added in phase P9
ADMIN_USERNAME, ADMIN_PASSWORD                              # bootstrap only
RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET   # phase P6
LOGIN_RATE_LIMIT_CAPACITY, LOGIN_RATE_LIMIT_WINDOW_SECONDS      # phase P8
SPRING_PROFILES_ACTIVE
```

## Definition of done (MVP storefront)

- [ ] P0 security fixes merged; leaked DB password rotated
- [ ] Admin uploads images → Google Drive + MySQL row; UI fetches by category without auth
- [ ] Guest browses/searches catalog with images
- [ ] Customer registers, manages addresses, carts, checks out
- [ ] Orders reserve stock atomically; admin manages statuses; no stock drift
- [ ] COD or sandbox payment marks orders paid
- [ ] Flyway owns the schema; tests cover security + checkout; README documents env
- [ ] (P9, post-MVP) Admin can write, image, and publish blog posts; guests see only published ones

**Recommended start:** T1 (rotate password) today, then T2–T14 as the first coding sprint, then jump straight to P1 (media) so the UI team can start building the storefront visuals while commerce flows are developed.

---

*End of plan.*
