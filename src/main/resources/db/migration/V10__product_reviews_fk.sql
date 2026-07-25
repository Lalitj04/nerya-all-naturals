-- Fix ProductReview associations (T56): customer_id becomes a proper FK to customers, one review
-- per customer per product is enforced at the DB level, and the denormalized customer_name column
-- is dropped since the customer's display name is always resolved through the FK.

ALTER TABLE product_reviews
    DROP COLUMN customer_name,
    MODIFY COLUMN is_verified_purchase BIT(1) NOT NULL DEFAULT 0,
    MODIFY COLUMN helpful_count INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_product_reviews_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    ADD CONSTRAINT uk_product_reviews_product_customer UNIQUE (product_id, customer_id),
    ADD KEY idx_product_reviews_customer (customer_id);
