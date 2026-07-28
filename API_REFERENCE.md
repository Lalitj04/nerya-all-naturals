# Nerya All Naturals — API Reference

Full reference for every REST endpoint in the backend, for frontend integration.

## Basics

- **Base URL:** `http://localhost:8080` (or your deployed host). All paths below are relative to this.
- **Content type:** `application/json` for all request/response bodies, except the media upload endpoint (`multipart/form-data`) and the payment webhook (raw JSON body, verified by signature).
- **Auth scheme:** JWT Bearer token. Send `Authorization: Bearer <token>` on every endpoint marked 🔒 below. Get a token from `POST /api/auth/login` or `POST /api/auth/register`.
- **Roles:** `ROLE_CUSTOMER` (registered shopper), `ROLE_ADMIN` (store admin), `ROLE_USER` (generic account, legacy). Endpoints marked **Customer** require `ROLE_CUSTOMER`; **Admin** require `ROLE_ADMIN`.
- **Pagination:** endpoints returning a `PagedResponse` accept standard Spring pagination query params: `?page=0&size=20&sort=fieldName,desc`. Default size 20, max 100.
- **Ownership pattern:** all "my X" endpoints (cart, orders, addresses, reviews) resolve the current user from the JWT — you never pass a customer/user id, and can never read/edit another customer's data this way.

### Standard error shape

Every error (except validation errors and the raw rate-limit response) returns:

```json
{
  "code": "NOT_FOUND",
  "message": "Product not found with ID: 5",
  "timestamp": "2026-07-25T10:15:30Z",
  "path": "/api/products/5",
  "fieldErrors": null
}
```

| HTTP status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `@Valid` request body failed field validation — `fieldErrors` is a `{field: message}` map |
| 400 | `BAD_REQUEST` | Malformed input (bad enum value, illegal argument) |
| 401 | `UNAUTHORIZED` | Missing/invalid JWT, or wrong login credentials |
| 403 | `FORBIDDEN` | Authenticated but lacking the required role, or not the resource owner |
| 404 | `NOT_FOUND` | Resource doesn't exist (or, for orders/reviews/blog drafts, isn't visible to you) |
| 409 | `CONFLICT` | Business-rule violation (duplicate name, illegal status transition, already reviewed, etc.) |
| 409 | `INSUFFICIENT_STOCK` | Not enough inventory to reserve the requested quantity |
| 413 | `PAYLOAD_TOO_LARGE` | Uploaded file exceeds 5 MB |
| 429 | `TOO_MANY_REQUESTS` | Login rate limit exceeded (raw JSON body, not the full `ErrorResponse` shape) |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

### PagedResponse envelope

```json
{
  "content": [ /* array of items */ ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "last": false
}
```

---

## 1. Auth — `/api/auth`

No token needed to call these; they issue one.

### `POST /api/auth/register` — create a customer account
**Request body:**
```json
{
  "username": "janedoe",       // required, 3-50 chars
  "email": "jane@example.com", // required, valid email
  "password": "password123",   // required, min 8 chars
  "firstName": "Jane",         // required
  "lastName": "Doe",           // required
  "phoneNumber": "9876543210"  // optional
}
```
**Response `201`:**
```json
{
  "token": "eyJhbGciOi...",
  "type": "Bearer",
  "refreshToken": "a1b2c3...",
  "username": "janedoe",
  "email": "jane@example.com",
  "roles": ["ROLE_CUSTOMER"],
  "customerId": 42
}
```
Errors: `409 CONFLICT` if username/email already taken.

### `POST /api/auth/login`
**Request:**
```json
{ "usernameOrEmail": "janedoe", "password": "password123" }
```
**Response `200`:** same shape as register's `AuthResponse` above.
**Response `401`:** plain text body `"Invalid username/email or password"` (not JSON — note this differs from the standard error shape). Also subject to brute-force rate limiting: 5 failed attempts/minute per IP and per attempted username (`429` after that, JSON body `{"code":"TOO_MANY_REQUESTS","message":"..."}`).

### `POST /api/auth/refresh` — exchange a refresh token for a new access token
**Request:** `{ "refreshToken": "a1b2c3..." }`
**Response `200`:** an `AuthResponse` (same shape as login), with a **new** `token` but the **same** `refreshToken`.
**Response `401`:** if the refresh token is invalid, expired, or revoked.

### `POST /api/auth/logout` — revoke a refresh token
**Request:** `{ "refreshToken": "a1b2c3..." }`
**Response:** `204 No Content`. Note: the still-live access token keeps working until it naturally expires — this only stops future refreshes.

### `GET /api/auth/validate` — check if a token is still valid
**Header:** `Authorization: Bearer <token>`
**Response `200`:** plain text `"Token is valid for user: janedoe"`.
**Response `401`:** plain text `"Missing or malformed Authorization header"` or `"Invalid token"`.

---

## 2. Customer profile & addresses 🔒 Customer

### `GET /api/customers/me` — my profile
**Response `200`:**
```json
{
  "id": 42,
  "customerName": "Jane Doe",
  "customerPhone": "9876543210",
  "dateOfBirth": "1995-06-10",
  "gender": "FEMALE",           // MALE | FEMALE | OTHER | PREFER_NOT_TO_SAY
  "username": "janedoe",
  "email": "jane@example.com",
  "createdAt": "2026-01-10T12:00:00",
  "updatedAt": "2026-01-10T12:00:00"
}
```

### `PUT /api/customers/me` — update my profile
**Request** (all fields optional, only non-null ones are changed):
```json
{
  "customerName": "Jane A. Doe",
  "customerPhone": "9876543210",
  "dateOfBirth": "1995-06-10",
  "gender": "FEMALE"
}
```
**Response `200`:** updated `CustomerResponse` (same shape as GET).

### `GET /api/customers/me/addresses` — list my addresses
**Response `200`:** array of:
```json
{
  "id": 7,
  "name": "Home",
  "addressLine1": "1 MG Road",
  "addressLine2": "Apt 4B",
  "city": "Mumbai",
  "state": "MH",
  "pinCode": "400001",
  "isDefault": true,
  "createdAt": "...",
  "updatedAt": "..."
}
```

### `POST /api/customers/me/addresses` — add an address
**Request:**
```json
{
  "name": "Home",                 // required
  "addressLine1": "1 MG Road",    // required
  "addressLine2": "Apt 4B",       // optional
  "city": "Mumbai",               // required
  "state": "MH",                  // required
  "pinCode": "400001",            // required
  "isDefault": true               // optional; setting true un-defaults any other address
}
```
**Response `201`:** the created `AddressResponse`.

### `PUT /api/customers/me/addresses/{addressId}` — update an address
Same request body as create. **Response `200`.** `404` if the address isn't yours.

### `DELETE /api/customers/me/addresses/{addressId}`
**Response:** `204 No Content`.

---

## 3. Catalog — Categories — `/api/categories`

### `GET /api/categories` — public — all active categories
**Response `200`:** array of:
```json
{
  "id": 3,
  "name": "Skincare",
  "description": "Natural skincare products",
  "imageUrl": null,
  "thumbnailUrl": "https://res.cloudinary.com/.../tile.jpg",  // category tile image, if any
  "isActive": true,
  "parentId": null,
  "parentName": null,
  "createdAt": "...",
  "updatedAt": "..."
}
```

### `GET /api/categories/{id}` — public — category by id
**Response `200`:** single `CategoryResponse`. `404` if not found.

### `GET /api/categories/parents` — public — top-level categories only
**Response `200`:** array of `CategoryResponse`.

### `POST /api/categories/admin` 🔒 Admin — create
**Request:**
```json
{
  "name": "Skincare",          // required
  "description": "...",        // optional
  "imageUrl": "...",           // optional, legacy field
  "isActive": true,            // optional, default true
  "parentId": null             // optional, for subcategories
}
```
**Response `201`.** `409` if name already exists.

### `PUT /api/categories/admin/{id}` 🔒 Admin — update
Same body as create. **Response `200`.** `409` on name collision or self-parenting.

### `DELETE /api/categories/admin/{id}` 🔒 Admin — soft-delete
**Response:** `204`. `409` if products or subcategories still reference it.

---

## 4. Catalog — Products — `/api/products`

### `GET /api/products` — public — search/filter/paginate
**Query params (all optional):** `q` (keyword against name/description), `categoryId`, `minPrice`, `maxPrice`, `inStock` (true/false), plus `page`/`size`/`sort`.
**Response `200`:** `PagedResponse<ProductResponse>` — see shape below.

### `GET /api/products/category/{categoryId}` — public
**Response `200`:** array of `ProductResponse` (unpaginated).

### `GET /api/products/{id}` — public
**Response `200`:** single `ProductResponse`. `404` if not found.

**`ProductResponse` shape:**
```json
{
  "id": 12,
  "name": "Lavender Body Oil",
  "sku": "LAV-001",
  "shortDescription": "...",
  "longDescription": "...",
  "price": 599.00,
  "sellingPrice": 499.00,
  "discountPercentage": 17,
  "brand": "Nerya",
  "weight": "250ml",
  "inStock": true,
  "quantity": 42,
  "minQuantity": 5,
  "isActive": true,
  "isFeatured": false,
  "averageRating": 4.5,
  "totalReviews": 12,
  "tags": ["organic", "vegan"],
  "metaTitle": "...",
  "metaDescription": "...",
  "createdAt": "...",
  "updatedAt": "...",
  "categoryId": 3,
  "categoryName": "Skincare",
  "images": [
    { "id": 55, "category": "PRODUCT", "publicUrl": "https://...", "altText": "...", "sortOrder": 0, "productId": 12, "categoryId": null, "blogId": null }
  ],
  "primaryImageUrl": "https://..."
}
```

### `GET /api/products/admin/all` 🔒 Admin — every product, including inactive
**Response `200`:** array of `ProductResponse`.

### `POST /api/products/admin` 🔒 Admin — create
**Request:**
```json
{
  "name": "Lavender Body Oil",      // required
  "sku": "LAV-001",                 // required, unique
  "shortDescription": "...",
  "longDescription": "...",
  "price": 599.00,                  // required, >= 0
  "sellingPrice": 499.00,           // required, >= 0
  "discountPercentage": 17,         // optional, 0-100
  "brand": "Nerya",
  "weight": "250ml",
  "inStock": true,
  "quantity": 42,                   // optional, >= 0
  "minQuantity": 5,                 // optional, >= 0
  "isActive": true,
  "isFeatured": false,
  "categoryId": 3,                  // required
  "tags": ["organic", "vegan"],
  "metaTitle": "...",
  "metaDescription": "...",
  "mediaAssetIds": [55, 56],        // optional: attach already-uploaded media assets (preferred)
  "imageUrls": [],                  // legacy: raw image URLs, pre-media-pipeline
  "isPrimaryImage": false           // legacy, paired with imageUrls
}
```
**Response `201`:** created `ProductResponse`. `404` if categoryId doesn't exist.

> Use `mediaAssetIds` (upload via the Media API first, see §5) rather than the legacy `imageUrls`/`isPrimaryImage` fields for new integrations.

### `PUT /api/products/admin/{id}` 🔒 Admin — update
Same body as create. **Response `200`.**

### `DELETE /api/products/admin/{id}` 🔒 Admin — soft-delete
**Response:** `204`.

---

## 5. Media (images) — `/api/media`

Images are stored in Cloudinary; this API manages the metadata (public URL + attributes). Categories: `HERO`, `BANNER`, `PRODUCT`, `CATEGORY`, `GALLERY`, `BLOG`. Uploads go into a Cloudinary folder derived from the category (e.g. `nerya/hero`).

Each asset can also carry **display copy + a click-through link** — mainly for `HERO`/`BANNER` slides that need an on-screen headline and a CTA. These fields (`title`, `subtitle`, `linkUrl`, `linkText`) are all optional and apply to any category.

### `POST /api/media/admin/upload` 🔒 Admin — upload an image
**Request:** `multipart/form-data`:
| Field | Required | Notes |
|---|---|---|
| `file` | yes | image/jpeg, image/png, image/webp, image/gif, ≤5 MB |
| `category` | yes | `HERO`\|`BANNER`\|`PRODUCT`\|`CATEGORY`\|`GALLERY`\|`BLOG` |
| `altText` | no | accessibility/SEO alt text |
| `title` | no | on-screen headline (e.g. hero/banner slide heading) |
| `subtitle` | no | supporting sub-heading text |
| `linkUrl` | no | click-through/redirection target (relative path or absolute URL) |
| `linkText` | no | CTA button label paired with `linkUrl` |
| `sortOrder` | no | display order (default 0; convention: 0 = cover/primary image) |
| `productId` | no | attach to a product |
| `categoryId` | no | attach to a category (tile image) |
| `blogId` | no | attach to a blog post (cover/inline image) |

**Response `201`:**
```json
{
  "id": 55, "category": "HERO", "publicUrl": "https://...", "altText": "Summer sale hero",
  "title": "Summer Sale", "subtitle": "Up to 40% off all naturals",
  "linkUrl": "/products?sale=summer", "linkText": "Shop now",
  "sortOrder": 0, "productId": null, "categoryId": null, "blogId": null
}
```
(`title`/`subtitle`/`linkUrl`/`linkText` are `null` when not set — e.g. for plain product images.)

### `POST /api/media/admin/register` 🔒 Admin — register an already-hosted image (by URL)
Records an image that already lives on a CDN/Cloudinary (or anywhere public) without re-uploading it.
**Request:**
```json
{
  "publicUrl": "https://res.cloudinary.com/.../hero.jpg", // required — the image URL
  "storageKey": "nerya/hero/abc123", // optional Cloudinary public id; auto-generated if omitted
  "category": "HERO",            // required
  "altText": "...",
  "title": "Summer Sale",        // optional display copy
  "subtitle": "Up to 40% off",   // optional
  "linkUrl": "/products?sale=summer", // optional click-through
  "linkText": "Shop now",        // optional CTA label
  "sortOrder": 0,
  "productId": null,
  "categoryId": null,
  "blogId": null
}
```
**Response `201`:** `MediaAssetResponse` (as above). `409` if that `storageKey` is already registered. (Assets registered without a real Cloudinary `storageKey` can't be hard-deleted from the provider.)

### `PUT /api/media/admin/{id}` 🔒 Admin — update metadata
**Request** (all optional, null = unchanged; send `""` to clear a text field):
`{ "altText": "...", "title": "...", "subtitle": "...", "linkUrl": "...", "linkText": "...", "sortOrder": 1, "category": "GALLERY", "isActive": true }`
**Response `200`.**

### `DELETE /api/media/admin/{id}?hard=false` 🔒 Admin
Default soft-delete (`isActive=false`). `?hard=true` also deletes the Cloudinary asset. **Response:** `204`.

### Public reads (no auth)
| Endpoint | Returns |
|---|---|
| `GET /api/media?category=&productId=` | filtered list of active `MediaAssetResponse` |
| `GET /api/media/category/{category}` | active assets in a category, sorted |
| `GET /api/media/product/{productId}` | a product's gallery |
| `GET /api/media/category-entity/{categoryId}` | a category's tile images |
| `GET /api/media/blog/{blogId}` | a blog post's cover + inline images |
| `GET /api/media/{id}` | single asset |

**Hero/banner rendering:** call `GET /api/media/category/HERO` (or `BANNER`), then for each item render `publicUrl` as the image, `title`/`subtitle` as the overlaid copy, and wrap the slide in a link to `linkUrl` (labelled `linkText` if you show a button). Items come back ordered by `sortOrder`.

---

## 6. Cart — `/api/cart` 🔒 Customer

Always resolved from the JWT — no customer id in the URL.

### `GET /api/cart` — my cart
**Response `200`:**
```json
{
  "id": 8,
  "items": [
    { "productId": 12, "sku": "LAV-001", "name": "Lavender Body Oil", "unitPrice": 499.00, "quantity": 2, "lineTotal": 998.00 }
  ],
  "totalItems": 2,
  "grandTotal": 998.00
}
```

### `POST /api/cart/items` — add an item (merges into existing line if already present)
**Request:** `{ "productId": 12, "quantity": 2 }` (quantity >= 1)
**Response `200`:** updated `CartResponse`. `409 INSUFFICIENT_STOCK` if not enough available.

### `PUT /api/cart/items/{productId}` — set exact quantity
**Request:** `{ "quantity": 3 }` (quantity 0 removes the line)
**Response `200`:** updated `CartResponse`.

### `DELETE /api/cart/items/{productId}` — remove a line
**Response `200`:** updated `CartResponse`.

### `DELETE /api/cart` — empty the cart
**Response `200`:** empty `CartResponse`.

---

## 7. Orders — `/api/orders`

### `POST /api/orders/checkout` 🔒 Customer
**Headers:** optional `Idempotency-Key: <any-string>` — replaying the same key from the same customer returns the original order instead of placing a duplicate.
**Request:** `{ "addressId": 7 }`
**Response `201`:**
```json
{
  "id": 100,
  "orderNumber": "NRY-2026-000100",
  "status": "PENDING",
  "paymentStatus": "UNPAID",
  "subtotal": 998.00,
  "shippingFee": 0.00,
  "discount": 0.00,
  "total": 998.00,
  "items": [
    { "productId": 12, "sku": "LAV-001", "productName": "Lavender Body Oil", "unitPrice": 499.00, "quantity": 2, "lineTotal": 998.00 }
  ],
  "shippingAddress": {
    "name": "Home", "phone": "9876543210", "line1": "1 MG Road", "line2": "Apt 4B",
    "city": "Mumbai", "state": "MH", "pincode": "400001"
  },
  "placedAt": "2026-07-25T10:00:00",
  "createdAt": "2026-07-25T10:00:00"
}
```
Errors: `409` if cart is empty, `409 INSUFFICIENT_STOCK` if stock ran out, `404` if address isn't yours.

`status` progresses: `PENDING → CONFIRMED → PACKED → SHIPPED → DELIVERED`, or `CANCELLED` (only while `PENDING`/`CONFIRMED`/`PACKED`).
`paymentStatus`: `UNPAID | PAID | REFUNDED | COD`.

### `GET /api/orders/me` 🔒 Customer — my order history (paged)
**Response `200`:** `PagedResponse<OrderResponse>`.

### `GET /api/orders/me/{orderNumber}` 🔒 Customer
**Response `200`:** single `OrderResponse`. `404` if not yours.

### `POST /api/orders/me/{orderNumber}/cancel` 🔒 Customer
Only allowed while `PENDING`/`CONFIRMED`/`PACKED`; releases reserved stock.
**Response `200`:** updated `OrderResponse` with `status: "CANCELLED"`. `409` if already shipped/delivered/cancelled.

### `GET /api/orders/admin` 🔒 Admin — all orders (paged, filterable)
**Query params (all optional):** `status`, `customerId`, `from` (ISO datetime), `to` (ISO datetime), plus `page`/`size`/`sort`.
**Response `200`:** `PagedResponse<OrderResponse>`.

### `GET /api/orders/admin/{orderNumber}` 🔒 Admin
**Response `200`:** single `OrderResponse`.

### `PUT /api/orders/admin/{orderNumber}/status` 🔒 Admin
**Request:** `{ "status": "CONFIRMED" }` (one of `PENDING|CONFIRMED|PACKED|SHIPPED|DELIVERED|CANCELLED`)
**Response `200`:** updated `OrderResponse`. `409` if the transition isn't legal (e.g. `PENDING → DELIVERED` directly).

---

## 8. Payments — `/api/payments`

### `POST /api/payments/create` 🔒 Customer
**Request:**
```json
{ "orderNumber": "NRY-2026-000100", "provider": "COD" }   // provider: "COD" | "RAZORPAY"
```
**Response `201`** for `COD` (payment is immediately successful, order flips to paid):
```json
{ "id": 1, "orderNumber": "NRY-2026-000100", "provider": "COD", "status": "SUCCESS", "amount": 998.00, "currency": "INR", "providerOrderId": null, "checkoutKeyId": null }
```
**Response `201`** for `RAZORPAY` (order created with the gateway; hand `checkoutKeyId` + `providerOrderId` + `amount`/`currency` to the Razorpay Checkout widget):
```json
{ "id": 2, "orderNumber": "NRY-2026-000101", "provider": "RAZORPAY", "status": "CREATED", "amount": 998.00, "currency": "INR", "providerOrderId": "order_abc123", "checkoutKeyId": "rzp_test_..." }
```
Errors: `409` if the order is already paid/COD, or if Razorpay isn't configured on the server.

`status` values: `CREATED | SUCCESS | FAILED | REFUNDED`.

### `POST /api/payments/webhook` — public, Razorpay-only
Not called by the frontend. Razorpay calls this directly with an `X-Razorpay-Signature` header; the raw body is signature-verified server-side. On `payment.captured` the order flips to `PAID`/`CONFIRMED`; on `payment.failed` the payment is marked `FAILED`. Duplicate deliveries are ignored (idempotent).

---

## 9. Product reviews

### `GET /api/products/{productId}/reviews` — public — paged
**Response `200`:** `PagedResponse` of:
```json
{
  "id": 30,
  "productId": 12,
  "customerName": "Jane Doe",
  "rating": 5,
  "title": "Love it!",
  "comment": "Great product, will buy again.",
  "verifiedPurchase": true,
  "createdAt": "2026-07-20T09:00:00"
}
```
`verifiedPurchase` is computed server-side (true only if you have a `DELIVERED` order containing this product) — you can't set it yourself.

### `POST /api/products/{productId}/reviews/me` 🔒 Customer — one review per product
**Request:**
```json
{ "rating": 5, "title": "Love it!", "comment": "Great product." }  // rating required 1-5; title max 1000 chars
```
**Response `201`:** created `ReviewResponse`. `409` if you've already reviewed this product.

### `PUT /api/products/{productId}/reviews/me` 🔒 Customer — edit your review
Same body as create. **Response `200`.** `404` if you haven't reviewed it yet.

### `DELETE /api/products/{productId}/reviews/me` 🔒 Customer
**Response:** `204`.

### `DELETE /api/reviews/admin/{reviewId}` 🔒 Admin — moderation delete
**Response:** `204`.

---

## 10. Blog — `/api/blogs`

Admin-authored posts with a draft → publish lifecycle. Images reuse the Media API (`category: "BLOG"`, `blogId: <post id>`).

### Public

**`GET /api/blogs`** — paged, **published posts only**, newest first.
**Response `200`:** `PagedResponse` of `BlogResponse` (shape below).

**`GET /api/blogs/{slug}`** — single published post by slug. `404` for drafts or unknown slugs (so a draft's existence can't be probed).

**`BlogResponse` shape:**
```json
{
  "id": 5,
  "title": "5 Benefits of Lavender Oil",
  "slug": "5-benefits-of-lavender-oil",
  "excerpt": "A quick look at why lavender oil belongs in your routine.",
  "content": "<p>Full HTML/Markdown body...</p>",
  "status": "PUBLISHED",
  "authorName": "Admin User",
  "publishedAt": "2026-07-20T09:00:00",
  "createdAt": "2026-07-18T09:00:00",
  "coverImageUrl": "https://res.cloudinary.com/.../cover.jpg",
  "images": [
    { "id": 60, "category": "BLOG", "publicUrl": "https://...", "altText": "...", "sortOrder": 0, "productId": null, "categoryId": null, "blogId": 5 }
  ]
}
```
`coverImageUrl` is the lowest-`sortOrder` active image (convention: upload the cover with `sortOrder=0`).

### Admin 🔒

**`GET /api/blogs/admin`** — paged, **all posts including drafts**.
**`GET /api/blogs/admin/{id}`** — single post by id (draft or published).

**`POST /api/blogs/admin`** — create a draft.
**Request:**
```json
{ "title": "5 Benefits of Lavender Oil", "excerpt": "...", "content": "<p>...</p>" }
```
(`title` required, max 200 chars; `excerpt` optional max 500; `content` optional — but publishing requires it non-empty)
**Response `201`:** `BlogResponse` with `status: "DRAFT"`. The slug is auto-generated from the title (`-2`, `-3`… appended on collision).

**`PUT /api/blogs/admin/{id}`** — edit. Same body as create. While still `DRAFT`, changing the title regenerates the slug; once published, the slug is frozen even if you edit the title afterward.

**`POST /api/blogs/admin/{id}/publish`** — publish. `409` if `content` is empty. Sets `publishedAt` on first publish only (a later re-publish after unpublishing keeps the original date).

**`POST /api/blogs/admin/{id}/unpublish`** — revert to `DRAFT`. Post is hidden from the public endpoints but not deleted.

**`DELETE /api/blogs/admin/{id}`** — permanently delete.

**Typical image workflow:** create the draft → `POST /api/media/admin/upload` with `category=BLOG`, `blogId=<id>`, `sortOrder=0` for the cover (and `sortOrder=1,2,...` for inline gallery images) → publish.

---

## 11. Inventory — `/api/inventory/admin` 🔒 Admin

Usually auto-managed by checkout/shipping; these endpoints are for manual stock corrections.

### `GET /api/inventory/admin/all`
**Response `200`:** array of:
```json
{
  "id": 12, "productId": 12, "productName": "Lavender Body Oil", "productSku": "LAV-001",
  "quantityOnHand": 42, "quantityReserved": 3, "quantitySold": 100,
  "availableQuantity": 39, "minStockLevel": 5, "maxStockLevel": 1000, "reorderQuantity": 50,
  "isLowStock": false, "lastUpdatedBy": "admin", "createdAt": "...", "updatedAt": "..."
}
```

### `GET /api/inventory/admin/{id}` / `GET /api/inventory/admin/product/{productId}`
**Response `200`:** single `InventoryResponse` as above. `404` if not found.

### `POST /api/inventory/admin` — create
**Request:**
```json
{
  "productId": 12,           // required
  "quantityOnHand": 42,      // required, >= 0
  "quantityReserved": 0,     // >= 0, default 0
  "quantitySold": 0,         // >= 0, default 0
  "minStockLevel": 5,        // >= 0, default 5
  "maxStockLevel": 1000,     // >= 0, default 1000
  "reorderQuantity": 50,     // >= 0, default 50
  "lastUpdatedBy": "admin"   // optional
}
```
**Response `201`.**

### `PUT /api/inventory/admin/{id}` — update
Same body as create. **Response `200`.**

### `DELETE /api/inventory/admin/{id}`
**Response:** `204`.

---

## 12. Users — `/api/users`

Low-level account management (separate from the customer-facing `/api/customers/me`). Mostly for admins managing raw accounts.

### `POST /api/users` 🔒 Admin — create a user
**Request:**
```json
{
  "username": "staffuser", "email": "staff@nerya.com", "password": "password123",
  "firstName": "Staff", "lastName": "Member", "phoneNumber": "...",
  "roles": ["ROLE_ADMIN"]
}
```
**Response `201`:** `UserResponse` (below). `409` on duplicate username/email.

### `GET /api/users` 🔒 Admin — list all users
**Response `200`:** array of `UserResponse`:
```json
{
  "id": 1, "username": "staffuser", "email": "staff@nerya.com",
  "firstName": "Staff", "lastName": "Member", "phoneNumber": "...",
  "isActive": true, "isEmailVerified": false, "lastLogin": "...",
  "roles": ["ROLE_ADMIN"], "createdAt": "...", "updatedAt": "..."
}
```

### `GET /api/users/{id}` 🔒 Admin or the user themself
**Response `200`:** `UserResponse`. `403` if you're neither admin nor the account owner.

### `PUT /api/users/{id}` 🔒 Admin or the user themself
Same body shape as create (password optional-in-practice but required by the DTO — send the current or a new password). A non-admin caller cannot change their own `roles` (silently ignored/rejected depending on service logic — role changes require admin).
**Response `200`:** updated `UserResponse`. `409` on username/email collision, `403` if not authorized.

### `DELETE /api/users/{id}` 🔒 Admin
**Response:** `204`.

### `GET /api/users/username/{username}` / `GET /api/users/email/{email}` 🔒 Admin or self
**Response `200`:** `UserResponse`. `403` if not authorized.

---

## Reference: enums

| Enum | Values |
|---|---|
| `User.Role` | `ROLE_USER`, `ROLE_ADMIN`, `ROLE_CUSTOMER` |
| `Customer.Gender` | `MALE`, `FEMALE`, `OTHER`, `PREFER_NOT_TO_SAY` |
| `OrderStatus` | `PENDING`, `CONFIRMED`, `PACKED`, `SHIPPED`, `DELIVERED`, `CANCELLED` |
| `PaymentStatus` (order-level) | `UNPAID`, `PAID`, `REFUNDED`, `COD` |
| `PaymentProvider` | `RAZORPAY`, `COD` |
| `PaymentTxnStatus` (payment-attempt-level) | `CREATED`, `SUCCESS`, `FAILED`, `REFUNDED` |
| `BlogStatus` | `DRAFT`, `PUBLISHED` |
| `MediaAsset.MediaCategory` | `HERO`, `BANNER`, `PRODUCT`, `CATEGORY`, `GALLERY`, `BLOG` |

---

## Quick auth flow for the frontend

1. `POST /api/auth/register` (or `/login`) → store `token` + `refreshToken`.
2. Send `Authorization: Bearer <token>` on every 🔒 request.
3. On `401` from a protected endpoint, call `POST /api/auth/refresh` with the stored `refreshToken` to get a new `token`, then retry.
4. On logout, call `POST /api/auth/logout` with the `refreshToken` and discard both tokens client-side.
