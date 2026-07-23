-- Links addresses.customer_id to customers.id with a real FK — it was previously a plain
-- BIGINT column with no referential integrity. Cascade delete lives at the JPA level
-- (Customer.addresses is cascade=ALL, orphanRemoval=true), matching how every other
-- parent/child relationship in this schema is handled (see V1 baseline).

ALTER TABLE addresses
    ADD CONSTRAINT fk_addresses_customer FOREIGN KEY (customer_id) REFERENCES customers (id);
