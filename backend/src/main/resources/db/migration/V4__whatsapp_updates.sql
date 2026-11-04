-- WhatsApp order updates: the customer's saved choice, and the choice made for each order at checkout.
ALTER TABLE users
    ADD COLUMN whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Customer agreed to order updates on WhatsApp' AFTER phone;

ALTER TABLE orders
    ADD COLUMN whatsapp_updates BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Send this order''s updates on WhatsApp' AFTER contact_phone;
