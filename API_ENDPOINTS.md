# Product Management API Endpoints

## Overview
Complete REST API implementation for product management in Nerya All Naturals application.

## Architecture
- **Entities**: Product, Category, ProductImage, ProductReview, Inventory
- **Repositories**: ProductRepository, CategoryRepository, ProductImageRepository, ProductReviewRepository, InventoryRepository
- **Services**: ProductService, CategoryService
- **Controllers**: ProductController, CategoryController
- **DTOs**: ProductRequest, ProductResponse, ProductImageResponse, CategoryResponse

---

## Public APIs (No Authentication Required)

### 1. Get All Active Products
**GET** `/api/products`

Returns all active products in the system.

**Response:**
```json
[
  {
    "id": 1,
    "name": "Organic Honey",
    "sku": "HNY001",
    "shortDescription": "Pure organic honey",
    "longDescription": "100% natural honey from organic farms...",
    "price": 499.00,
    "sellingPrice": 399.00,
    "discountPercentage": 20,
    "brand": "Nerya Naturals",
    "weight": "500g",
    "inStock": true,
    "quantity": 100,
    "isActive": true,
    "isFeatured": true,
    "averageRating": 4.5,
    "totalReviews": 25,
    "tags": ["organic", "natural", "honey"],
    "primaryImageUrl": "https://example.com/image.jpg",
    "images": [...],
    "categoryId": 1,
    "categoryName": "Organic Foods"
  }
]
```

### 2. Get Products by Category
**GET** `/api/products/category/{categoryId}`

Returns all active products in a specific category.

**Parameters:**
- `categoryId` (path) - The category ID

**Response:** Same as Get All Products

### 3. Get Product by ID
**GET** `/api/products/{id}`

Returns detailed information about a specific product.

**Parameters:**
- `id` (path) - The product ID

**Response:**
```json
{
  "id": 1,
  "name": "Organic Honey",
  "sku": "HNY001",
  "shortDescription": "Pure organic honey",
  "longDescription": "100% natural honey...",
  "price": 499.00,
  "sellingPrice": 399.00,
  "discountPercentage": 20,
  "brand": "Nerya Naturals",
  "weight": "500g",
  "inStock": true,
  "quantity": 100,
  "isActive": true,
  "isFeatured": true,
  "averageRating": 4.5,
  "totalReviews": 25,
  "tags": ["organic", "natural", "honey"],
  "primaryImageUrl": "https://example.com/image.jpg",
  "images": [...],
  "categoryId": 1,
  "categoryName": "Organic Foods"
}
```

---

## Admin APIs (Authentication Required - Admin Role)

### 4. Create Category
**POST** `/api/categories/admin`

Creates a new category. Only accessible by ADMIN.

**Authentication:** Required (Admin role)

**Request Body:**
```json
{
  "name": "Organic Foods",
  "description": "Organic and natural food products",
  "imageUrl": "https://example.com/category.jpg",
  "isActive": true,
  "parentId": null
}
```

**Response:** Created category details with ID, timestamps, etc.

**Error Responses:**
- `400 Bad Request` - Invalid data (e.g., category name already exists)
- `401 Unauthorized` - Not authenticated
- `403 Forbidden` - Not an admin user

### 5. Create Product
**POST** `/api/products/admin`

Creates a new product. Only accessible by ADMIN.

**Authentication:** Required (Admin role)

**Request Body:**
```json
{
  "name": "Organic Honey",
  "sku": "HNY001",
  "shortDescription": "Pure organic honey",
  "longDescription": "100% natural honey from organic farms...",
  "price": 499.00,
  "sellingPrice": 399.00,
  "discountPercentage": 20,
  "brand": "Nerya Naturals",
  "weight": "500g",
  "inStock": true,
  "quantity": 100,
  "minQuantity": 10,
  "isActive": true,
  "isFeatured": true,
  "categoryId": 1,
  "tags": ["organic", "natural", "honey"],
  "metaTitle": "SEO Title",
  "metaDescription": "SEO Description",
  "imageUrls": [
    "https://example.com/image1.jpg",
    "https://example.com/image2.jpg"
  ],
  "isPrimaryImage": true
}
```

**Response:** Created product details (same format as ProductResponse)

**Error Responses:**
- `400 Bad Request` - Invalid data (e.g., SKU already exists)
- `401 Unauthorized` - Not authenticated
- `403 Forbidden` - Not an admin user

### 6. Get All Products (Including Inactive)
**GET** `/api/products/admin/all`

Returns all products including inactive ones. Only accessible by ADMIN.

**Authentication:** Required (Admin role)

**Response:** Same as Get All Products (includes inactive)

### 7. Update Product
**PUT** `/api/products/admin/{id}`

Updates product details. Only accessible by ADMIN.

**Authentication:** Required (Admin role)

**Parameters:**
- `id` (path) - The product ID

**Request Body:**
```json
{
  "name": "Updated Product Name",
  "sku": "UPDATED001",
  "shortDescription": "Short description",
  "longDescription": "Long description...",
  "price": 499.00,
  "sellingPrice": 399.00,
  "discountPercentage": 20,
  "brand": "Nerya Naturals",
  "weight": "500g",
  "inStock": true,
  "quantity": 100,
  "minQuantity": 10,
  "isActive": true,
  "isFeatured": true,
  "categoryId": 1,
  "tags": ["organic", "natural", "honey"],
  "metaTitle": "SEO Title",
  "metaDescription": "SEO Description",
  "imageUrls": [
    "https://example.com/image1.jpg",
    "https://example.com/image2.jpg"
  ],
  "isPrimaryImage": true
}
```

**Response:** Updated product details (same format as ProductResponse)

**Error Responses:**
- `404 Not Found` - Product not found
- `400 Bad Request` - Invalid data (e.g., SKU already exists)
- `401 Unauthorized` - Not authenticated
- `403 Forbidden` - Not an admin user

### 8. Delete Product
**DELETE** `/api/products/admin/{id}`

Soft deletes a product (sets isActive to false). Only accessible by ADMIN.

**Authentication:** Required (Admin role)

**Parameters:**
- `id` (path) - The product ID

**Response:**
```json
"Product deleted successfully"
```

**Error Responses:**
- `404 Not Found` - Product not found
- `401 Unauthorized` - Not authenticated
- `403 Forbidden` - Not an admin user

---

## Category APIs (Public)

### 9. Get All Categories
**GET** `/api/categories`

Returns all active categories.

**Response:**
```json
[
  {
    "id": 1,
    "name": "Organic Foods",
    "description": "Organic and natural food products",
    "imageUrl": "https://example.com/category.jpg",
    "isActive": true,
    "parentId": null,
    "parentName": null
  }
]
```

### 10. Get Category by ID
**GET** `/api/categories/{id}`

Returns category details.

**Parameters:**
- `id` (path) - The category ID

**Response:** Category details

### 11. Get Parent Categories
**GET** `/api/categories/parents`

Returns only parent categories (categories without a parent).

**Response:** List of parent categories

---

## Data Models

### Product Entity Fields
- `id` - Product ID
- `name` - Product name
- `sku` - Stock Keeping Unit (unique)
- `shortDescription` - Brief description
- `longDescription` - Detailed description
- `price` - Original price
- `sellingPrice` - Selling price
- `discountPercentage` - Discount percentage
- `brand` - Brand name
- `weight` - Product weight
- `inStock` - Stock availability
- `quantity` - Available quantity
- `minQuantity` - Minimum reorder quantity
- `isActive` - Active status
- `isFeatured` - Featured product flag
- `averageRating` - Average rating (0-5)
- `totalReviews` - Total number of reviews
- `tags` - Product tags
- `metaTitle` - SEO meta title
- `metaDescription` - SEO meta description
- `category` - Associated category
- `images` - Product images
- `reviews` - Product reviews

### Category Entity Fields
- `id` - Category ID
- `name` - Category name
- `description` - Category description
- `imageUrl` - Category image
- `isActive` - Active status
- `parentCategory` - Parent category (for hierarchical categories)
- `subCategories` - Child categories

---

## Notes
1. All timestamps (`createdAt`, `updatedAt`) are automatically managed by `BaseEntity`
2. Product images are managed through the `ProductImage` entity with support for primary images
3. Reviews and ratings are tracked in the `ProductReview` entity
4. Inventory management is handled through the `Inventory` entity
5. Soft delete is implemented for products (sets `isActive` to false)
6. Admin endpoints require `ROLE_ADMIN` authority

