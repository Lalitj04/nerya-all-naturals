-- Lets a customer mark one address as their default shipping address.

ALTER TABLE addresses
    ADD COLUMN is_default BIT(1) DEFAULT NULL AFTER pin_code;
