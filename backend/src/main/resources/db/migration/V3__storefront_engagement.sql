-- =====================================================================
-- Phase 6: storefront engagement
--  * coupons.is_public: coupons the shop advertises in "Special offers"
--  * contact_messages: Contact Us form submissions (admin inbox in Phase 16)
-- =====================================================================

ALTER TABLE coupons
    ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'Shown publicly in the Special offers section' AFTER active;

CREATE TABLE contact_messages (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NULL COMMENT 'Set when the sender was logged in',
    name       VARCHAR(120)    NOT NULL,
    email      VARCHAR(190)    NOT NULL,
    phone      VARCHAR(15)     NULL,
    subject    VARCHAR(150)    NOT NULL,
    message    TEXT            NOT NULL,
    status     VARCHAR(20)     NOT NULL DEFAULT 'NEW' COMMENT 'NEW, READ, REPLIED, CLOSED',
    ip_address VARCHAR(45)     NULL,
    user_agent VARCHAR(255)    NULL,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_contact_messages_status_created (status, created_at),
    KEY idx_contact_messages_email (email),
    CONSTRAINT fk_contact_messages_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_contact_messages_status CHECK (status IN ('NEW', 'READ', 'REPLIED', 'CLOSED'))
) ENGINE=InnoDB;
