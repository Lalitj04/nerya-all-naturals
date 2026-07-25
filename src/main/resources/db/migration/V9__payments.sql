-- Payments (T52/T53). One row per payment attempt against an order — COD is recorded as an
-- immediately-successful "payment" so the order/payment relationship stays uniform regardless of
-- provider. Gateway attempts (Razorpay) start CREATED and are flipped to SUCCESS/FAILED by the
-- signature-verified webhook (T54).

CREATE TABLE payments (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id             BIGINT       NOT NULL,
    provider             VARCHAR(20)  NOT NULL,
    provider_order_id    VARCHAR(100),
    provider_payment_id  VARCHAR(100),
    amount               DECIMAL(12,2) NOT NULL,
    currency             VARCHAR(10)  NOT NULL DEFAULT 'INR',
    status               VARCHAR(20)  NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uk_payments_provider_order_id UNIQUE (provider_order_id),
    KEY idx_payments_order (order_id),
    KEY idx_payments_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
