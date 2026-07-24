-- Orders & checkout (T45). An order is a permanent, immutable snapshot of what the customer
-- bought: line items copy the product's sku/name/price at purchase time (T46), so later catalog
-- edits never rewrite order history. Inventory reservation/sale is driven by order status (T49).

CREATE TABLE orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number    VARCHAR(40)  NOT NULL,
    customer_id     BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    payment_status  VARCHAR(20)  NOT NULL,
    subtotal        DECIMAL(12,2) NOT NULL,
    shipping_fee    DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    discount        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total           DECIMAL(12,2) NOT NULL,
    -- Optional client-supplied idempotency key; a replay with the same (customer, key) returns
    -- the original order instead of placing a second one (T50).
    idempotency_key VARCHAR(80),
    -- Shipping address snapshot (T45): frozen at checkout so editing/deleting the address later
    -- never mutates a placed order.
    ship_name       VARCHAR(100) NOT NULL,
    ship_phone      VARCHAR(20),
    ship_line1      VARCHAR(255) NOT NULL,
    ship_line2      VARCHAR(255),
    ship_city       VARCHAR(100) NOT NULL,
    ship_state      VARCHAR(100) NOT NULL,
    ship_pincode    VARCHAR(20)  NOT NULL,
    placed_at       DATETIME(6),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT uk_orders_customer_idempotency UNIQUE (customer_id, idempotency_key),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    KEY idx_orders_customer (customer_id),
    KEY idx_orders_status (status),
    KEY idx_orders_placed_at (placed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT        NOT NULL,
    product_id    BIGINT        NOT NULL,
    sku           VARCHAR(100)  NOT NULL,
    product_name  VARCHAR(255)  NOT NULL,
    unit_price    DECIMAL(12,2) NOT NULL,
    quantity      INT           NOT NULL,
    line_total    DECIMAL(12,2) NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    KEY idx_order_items_order (order_id),
    KEY idx_order_items_product (product_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
