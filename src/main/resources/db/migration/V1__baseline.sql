-- Baseline schema capturing the tables Hibernate had been auto-generating (ddl-auto: update).
-- Column types/nullability were captured from a fresh Hibernate 6.5 + MySQLDialect schema
-- export against MySQL 8, so this migration and the entities agree exactly under
-- ddl-auto: validate.

CREATE TABLE users (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    username            VARCHAR(50)  NOT NULL,
    email               VARCHAR(100) NOT NULL,
    password            VARCHAR(255) NOT NULL,
    first_name          VARCHAR(50)  NOT NULL,
    last_name           VARCHAR(50)  NOT NULL,
    phone_number        VARCHAR(20),
    is_active           BIT(1) DEFAULT NULL,
    is_email_verified   BIT(1) DEFAULT NULL,
    last_login          DATETIME(6),
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role    ENUM('ROLE_ADMIN','ROLE_CUSTOMER','ROLE_USER') NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE customers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    customer_name   VARCHAR(100) NOT NULL,
    customer_phone  VARCHAR(20),
    date_of_birth   DATE,
    gender          ENUM('FEMALE','MALE','OTHER','PREFER_NOT_TO_SAY') DEFAULT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    CONSTRAINT uk_customers_user UNIQUE (user_id),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE addresses (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id     BIGINT NOT NULL,
    name            VARCHAR(100) NOT NULL,
    address_line_1  VARCHAR(255) NOT NULL,
    address_line_2  VARCHAR(255),
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100) NOT NULL,
    pin_code        VARCHAR(20)  NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE categories (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    image_url       VARCHAR(255),
    is_active       BIT(1) DEFAULT NULL,
    parent_id       BIGINT,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    CONSTRAINT uk_categories_name UNIQUE (name),
    KEY idx_categories_parent (parent_id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                  VARCHAR(200)  NOT NULL,
    sku                   VARCHAR(100)  NOT NULL,
    short_description     VARCHAR(2000),
    long_description      TEXT,
    price                 DECIMAL(19,2) NOT NULL,
    selling_price         DECIMAL(19,2) NOT NULL,
    discount_percentage   INT,
    brand                 VARCHAR(100),
    weight                VARCHAR(50),
    in_stock              BIT(1) DEFAULT NULL,
    quantity              INT,
    min_quantity          INT,
    is_active             BIT(1) DEFAULT NULL,
    is_featured           BIT(1) DEFAULT NULL,
    rating                DECIMAL(38,2),
    total_reviews         INT,
    category_id           BIGINT NOT NULL,
    meta_title            VARCHAR(255),
    meta_description      TEXT,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    CONSTRAINT uk_products_sku UNIQUE (sku),
    KEY idx_products_category (category_id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT chk_products_discount_percentage CHECK (discount_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_products_min_quantity CHECK (min_quantity >= 0),
    CONSTRAINT chk_products_quantity CHECK (quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_images (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id      BIGINT NOT NULL,
    image_url       VARCHAR(500) NOT NULL,
    alt_text        VARCHAR(200),
    is_primary      BIT(1) DEFAULT NULL,
    display_order   INT,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    KEY idx_product_images_product (product_id),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_reviews (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id              BIGINT NOT NULL,
    customer_id             BIGINT NOT NULL,
    customer_name           VARCHAR(100) NOT NULL,
    rating                  INT NOT NULL,
    title                   VARCHAR(1000),
    comment                 TEXT,
    is_verified_purchase    BIT(1) DEFAULT NULL,
    helpful_count           INT,
    created_at              DATETIME(6) NOT NULL,
    updated_at              DATETIME(6) NOT NULL,
    KEY idx_product_reviews_product (product_id),
    CONSTRAINT fk_product_reviews_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_product_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_tags (
    product_id  BIGINT NOT NULL,
    tag         VARCHAR(255) NOT NULL,
    PRIMARY KEY (product_id, tag),
    CONSTRAINT fk_product_tags_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id          BIGINT NOT NULL,
    quantity_on_hand    INT NOT NULL,
    quantity_reserved   INT,
    quantity_sold       INT,
    min_stock_level     INT,
    max_stock_level     INT,
    reorder_quantity    INT,
    last_updated_by     VARCHAR(255),
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    CONSTRAINT uk_inventory_product UNIQUE (product_id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
