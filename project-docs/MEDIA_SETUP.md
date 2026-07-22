# Media (Google Drive) setup

Images are stored in Google Drive; only their metadata and a public URL live in MySQL
(`media_assets` table, `MediaAsset` entity). This is the custom feature described in
`IMPLEMENTATION_TASK_PLAN.md` phase P1 (T15–T26).

## 1. Google Cloud setup (one-time, do this in your own Google account)

1. Create a Google Cloud project and enable the **Google Drive API** for it.
2. Create a **service account** in that project, then generate and download a JSON key
   for it. Never commit this file — it's referenced by path via `GOOGLE_DRIVE_CREDENTIALS`.
3. In Google Drive, create a parent folder (e.g. `nerya-media`) with one subfolder per
   category: `hero`, `banner`, `product`, `category`, `gallery`.
4. Share the parent folder with the service account's email address (found in the JSON
   key as `client_email`) as **Editor**.
5. Open each subfolder in the browser and copy its folder ID from the URL
   (`https://drive.google.com/drive/folders/<FOLDER_ID>`).

You should end up with one JSON key file and five folder IDs.

## 2. Environment variables

| Variable | Purpose | Required? |
|---|---|---|
| `GOOGLE_DRIVE_CREDENTIALS` | Absolute path to the service-account JSON key | No — Drive integration is disabled (no bean created, app still boots) when unset |
| `DRIVE_FOLDER_HERO` | Drive folder ID for `HERO` category uploads | Required to upload/register HERO assets |
| `DRIVE_FOLDER_BANNER` | Drive folder ID for `BANNER` category uploads | Required to upload/register BANNER assets |
| `DRIVE_FOLDER_PRODUCT` | Drive folder ID for `PRODUCT` category uploads | Required to upload/register PRODUCT assets |
| `DRIVE_FOLDER_CATEGORY` | Drive folder ID for `CATEGORY` category uploads | Required to upload/register CATEGORY assets |
| `DRIVE_FOLDER_GALLERY` | Drive folder ID for `GALLERY` category uploads | Required to upload/register GALLERY assets |

These map to `media.google.credentials-path` and `media.google.folders.*` in
`application.yml`, bound via `GoogleDriveProperties`.

Without `GOOGLE_DRIVE_CREDENTIALS` set, the `Drive` bean is never created and every
`GoogleDriveService` call (`upload`, `registerExisting`, hard delete) fails fast with
`IllegalStateException: Google Drive is not configured; set GOOGLE_DRIVE_CREDENTIALS to
enable media uploads`. The public read endpoints and admin metadata endpoints (`update`,
soft `delete`) work regardless, since they never touch Drive.

## 3. Uploaded file constraints

- Allowed MIME types: `image/jpeg`, `image/png`, `image/webp`, `image/gif`.
- Max size: 5 MB (`spring.servlet.multipart.max-file-size`/`max-request-size`). Larger
  uploads get a `413 PAYLOAD_TOO_LARGE` JSON error, not a raw container error.
- The public URL convention is `https://drive.google.com/uc?export=view&id=<FILE_ID>`,
  stored in `media_assets.public_url` — this is what the UI renders directly in `<img>`.

## 4. API surface

All endpoints are grouped under the **Media** tag in Swagger UI (`/swagger-ui`, disabled
in the `prod` profile — see `application-prod.yml.example`).

| Method | Path | Auth |
|---|---|---|
| POST | `/api/media/admin/upload` | Admin (multipart: `file`, `category`, `altText?`, `sortOrder?`, `productId?`) |
| POST | `/api/media/admin/register` | Admin (JSON: `driveFileId`, `category`, `altText?`, `sortOrder?`, `productId?`) |
| PUT | `/api/media/admin/{id}` | Admin (JSON: `altText?`, `sortOrder?`, `category?`, `isActive?`) |
| DELETE | `/api/media/admin/{id}?hard=false` | Admin (`hard=true` also deletes the Drive file) |
| GET | `/api/media?category=&productId=` | Public |
| GET | `/api/media/category/{category}` | Public |
| GET | `/api/media/product/{productId}` | Public |
| GET | `/api/media/{id}` | Public |

`GET /api/products/{id}` also returns an `images` array populated from `media_assets` for
that product, falling back to the legacy `product_images` rows for products that predate
this feature. `POST`/`PUT /api/products/admin/**` accept an optional `mediaAssetIds` array
to attach already-uploaded assets to a product.
