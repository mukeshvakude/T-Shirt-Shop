-- =====================================================================
-- T-Shirt Shop: core schema (MySQL 8, InnoDB, utf8mb4)
-- Owned by Flyway. Hibernate runs with ddl-auto=validate.
-- Conventions: BIGINT UNSIGNED ids, DATETIME(6) UTC timestamps,
-- DECIMAL(12,2) INR money, enums stored as VARCHAR.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Users, roles, tokens, addresses
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email                  VARCHAR(190)    NOT NULL,
    phone                  VARCHAR(15)     NULL,
    password_hash          VARCHAR(100)    NOT NULL,
    full_name              VARCHAR(120)    NOT NULL,
    enabled                BOOLEAN         NOT NULL DEFAULT TRUE,
    email_verified         BOOLEAN         NOT NULL DEFAULT FALSE,
    failed_login_attempts  INT             NOT NULL DEFAULT 0,
    locked_until           DATETIME(6)     NULL,
    last_login_at          DATETIME(6)     NULL,
    created_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone),
    KEY idx_users_created_at (created_at)
) ENGINE=InnoDB;

CREATE TABLE roles (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(40)     NOT NULL,
    description VARCHAR(200)    NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB;

CREATE TABLE user_roles (
    user_id    BIGINT UNSIGNED NOT NULL,
    role_id    BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE refresh_tokens (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    token_hash  CHAR(64)        NOT NULL,
    expires_at  DATETIME(6)     NOT NULL,
    revoked_at  DATETIME(6)     NULL,
    replaced_by BIGINT UNSIGNED NULL,
    user_agent  VARCHAR(255)    NULL,
    ip_address  VARCHAR(45)     NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    KEY idx_refresh_tokens_expires (expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE password_reset_tokens (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64)        NOT NULL,
    expires_at DATETIME(6)     NOT NULL,
    used_at    DATETIME(6)     NULL,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_tokens_hash (token_hash),
    KEY idx_password_reset_tokens_user (user_id),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE addresses (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    label      VARCHAR(30)     NULL COMMENT 'Home, Work, ...',
    full_name  VARCHAR(120)    NOT NULL,
    phone      VARCHAR(15)     NOT NULL,
    line1      VARCHAR(200)    NOT NULL,
    line2      VARCHAR(200)    NULL,
    landmark   VARCHAR(120)    NULL,
    city       VARCHAR(80)     NOT NULL,
    state      VARCHAR(80)     NOT NULL,
    pincode    CHAR(6)         NOT NULL,
    country    VARCHAR(60)     NOT NULL DEFAULT 'India',
    is_default BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_addresses_user (user_id, is_default),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_addresses_pincode CHECK (pincode REGEXP '^[1-9][0-9]{5}$')
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT UNSIGNED NULL,
    code        VARCHAR(40)     NOT NULL COMMENT 'Stable code used by the app, e.g. TSHIRTS',
    name        VARCHAR(80)     NOT NULL,
    slug        VARCHAR(100)    NOT NULL,
    description VARCHAR(500)    NULL,
    image_url   VARCHAR(500)    NULL,
    sort_order  INT             NOT NULL DEFAULT 0,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_code (code),
    UNIQUE KEY uk_categories_slug (slug),
    KEY idx_categories_parent (parent_id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE product_sizes (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code       VARCHAR(10)     NOT NULL,
    label      VARCHAR(30)     NOT NULL,
    size_group VARCHAR(20)     NOT NULL COMMENT 'APPAREL or WAIST',
    sort_order INT             NOT NULL DEFAULT 0,
    active     BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sizes_code (code)
) ENGINE=InnoDB;

CREATE TABLE product_colors (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code       VARCHAR(20)     NOT NULL,
    name       VARCHAR(40)     NOT NULL,
    hex        CHAR(7)         NOT NULL,
    sort_order INT             NOT NULL DEFAULT 0,
    active     BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_colors_code (code),
    CONSTRAINT chk_product_colors_hex CHECK (hex REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE=InnoDB;

CREATE TABLE products (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    category_id       BIGINT UNSIGNED NOT NULL,
    name              VARCHAR(150)    NOT NULL,
    slug              VARCHAR(170)    NOT NULL,
    short_description VARCHAR(300)    NULL,
    description       TEXT            NULL,
    base_price        DECIMAL(12,2)   NOT NULL,
    discount_price    DECIMAL(12,2)   NULL COMMENT 'Selling price when on discount',
    status            VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, ACTIVE, INACTIVE, ARCHIVED',
    featured          BOOLEAN         NOT NULL DEFAULT FALSE,
    new_arrival       BOOLEAN         NOT NULL DEFAULT FALSE,
    is_customizable   BOOLEAN         NOT NULL DEFAULT FALSE,
    material          VARCHAR(100)    NULL,
    fit               VARCHAR(50)     NULL,
    care_instructions VARCHAR(300)    NULL,
    avg_rating        DECIMAL(3,2)    NOT NULL DEFAULT 0.00,
    review_count      INT             NOT NULL DEFAULT 0,
    sold_count        INT             NOT NULL DEFAULT 0,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version           BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_slug (slug),
    KEY idx_products_category_status (category_id, status),
    KEY idx_products_status_created (status, created_at),
    KEY idx_products_status_price (status, base_price),
    KEY idx_products_sold (sold_count),
    FULLTEXT KEY ft_products_name_desc (name, short_description, description),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT chk_products_base_price CHECK (base_price >= 0),
    CONSTRAINT chk_products_discount_price CHECK (discount_price IS NULL OR (discount_price >= 0 AND discount_price <= base_price)),
    CONSTRAINT chk_products_rating CHECK (avg_rating BETWEEN 0 AND 5)
) ENGINE=InnoDB;

CREATE TABLE product_images (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id  BIGINT UNSIGNED NOT NULL,
    color_id    BIGINT UNSIGNED NULL COMMENT 'Image specific to a color variant',
    image_url   VARCHAR(500)    NOT NULL COMMENT 'Public URL or app-relative path',
    storage_key VARCHAR(255)    NULL COMMENT 'Key in StorageService when uploaded',
    alt_text    VARCHAR(200)    NULL,
    sort_order  INT             NOT NULL DEFAULT 0,
    is_primary  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_product_images_product (product_id, sort_order),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_images_color FOREIGN KEY (color_id) REFERENCES product_colors (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- Stock per SKU (product + size + color). Available = on_hand - reserved.
CREATE TABLE inventory (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id          BIGINT UNSIGNED NOT NULL,
    size_id             BIGINT UNSIGNED NOT NULL,
    color_id            BIGINT UNSIGNED NOT NULL,
    sku                 VARCHAR(64)     NOT NULL,
    quantity_on_hand    INT             NOT NULL DEFAULT 0,
    quantity_reserved   INT             NOT NULL DEFAULT 0,
    low_stock_threshold INT             NOT NULL DEFAULT 5,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_variant (product_id, size_id, color_id),
    UNIQUE KEY uk_inventory_sku (sku),
    KEY idx_inventory_size (size_id),
    KEY idx_inventory_color (color_id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_size FOREIGN KEY (size_id) REFERENCES product_sizes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_color FOREIGN KEY (color_id) REFERENCES product_colors (id) ON DELETE RESTRICT,
    CONSTRAINT chk_inventory_on_hand CHECK (quantity_on_hand >= 0),
    CONSTRAINT chk_inventory_reserved CHECK (quantity_reserved >= 0 AND quantity_reserved <= quantity_on_hand)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Customization
-- ---------------------------------------------------------------------
CREATE TABLE customization_options (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    option_type VARCHAR(30)     NOT NULL COMMENT 'TSHIRT_TYPE, PRINT_PLACEMENT, FONT',
    code        VARCHAR(40)     NOT NULL,
    label       VARCHAR(80)     NOT NULL,
    metadata    JSON            NULL COMMENT 'e.g. print area box, mockup image paths, font family',
    sort_order  INT             NOT NULL DEFAULT 0,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_customization_options_type_code (option_type, code)
) ENGINE=InnoDB;

CREATE TABLE customization_charges (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    option_id   BIGINT UNSIGNED NULL,
    charge_type VARCHAR(30)     NOT NULL COMMENT 'TSHIRT_TYPE, PRINT_PLACEMENT, TEXT, DESIGN, SIZE, COLOR',
    code        VARCHAR(40)     NOT NULL,
    amount      DECIMAL(12,2)   NOT NULL,
    description VARCHAR(200)    NULL,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version     BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customization_charges_type_code (charge_type, code),
    KEY idx_customization_charges_option (option_id),
    CONSTRAINT fk_customization_charges_option FOREIGN KEY (option_id) REFERENCES customization_options (id) ON DELETE SET NULL,
    CONSTRAINT chk_customization_charges_amount CHECK (amount >= 0)
) ENGINE=InnoDB;

CREATE TABLE custom_designs (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    design_code       VARCHAR(20)     NOT NULL COMMENT 'Public reference, e.g. DSN-7K2M9Q',
    user_id           BIGINT UNSIGNED NOT NULL,
    base_product_id   BIGINT UNSIGNED NOT NULL COMMENT 'Blank customizable T-Shirt product',
    name              VARCHAR(100)    NULL,
    tshirt_type       VARCHAR(40)     NOT NULL,
    color_code        VARCHAR(20)     NOT NULL,
    size_code         VARCHAR(10)     NOT NULL,
    placements        JSON            NOT NULL COMMENT 'Array of print placement codes',
    canvas_front      JSON            NULL COMMENT 'Fabric.js JSON for the front side',
    canvas_back       JSON            NULL COMMENT 'Fabric.js JSON for the back side',
    text_items        JSON            NULL COMMENT 'Text content, font, size, color per item',
    premium_design    BOOLEAN         NOT NULL DEFAULT FALSE,
    preview_front_key VARCHAR(255)    NULL,
    preview_back_key  VARCHAR(255)    NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, IN_CART, ORDERED, ARCHIVED',
    locked            BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'True once ordered; edits create a copy',
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version           BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_custom_designs_code (design_code),
    KEY idx_custom_designs_user (user_id, created_at),
    KEY idx_custom_designs_product (base_product_id),
    CONSTRAINT fk_custom_designs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_custom_designs_product FOREIGN KEY (base_product_id) REFERENCES products (id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE design_assets (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED NOT NULL,
    custom_design_id  BIGINT UNSIGNED NULL COMMENT 'Null until the design is saved',
    storage_key       VARCHAR(255)    NOT NULL,
    original_filename VARCHAR(255)    NOT NULL,
    mime_type         VARCHAR(50)     NOT NULL,
    size_bytes        BIGINT          NOT NULL,
    width_px          INT             NULL,
    height_px         INT             NULL,
    sha256            CHAR(64)        NOT NULL,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_design_assets_storage_key (storage_key),
    KEY idx_design_assets_design (custom_design_id),
    KEY idx_design_assets_user (user_id),
    CONSTRAINT fk_design_assets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_design_assets_design FOREIGN KEY (custom_design_id) REFERENCES custom_designs (id) ON DELETE SET NULL,
    CONSTRAINT chk_design_assets_mime CHECK (mime_type IN ('image/png', 'image/jpeg')),
    CONSTRAINT chk_design_assets_size CHECK (size_bytes > 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Cart and coupons
-- ---------------------------------------------------------------------
CREATE TABLE carts (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    coupon_code VARCHAR(40)    NULL,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version    BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_carts_user (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE cart_items (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cart_id          BIGINT UNSIGNED NOT NULL,
    inventory_id     BIGINT UNSIGNED NOT NULL,
    custom_design_id BIGINT UNSIGNED NULL,
    quantity         INT             NOT NULL,
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_cart_items_cart (cart_id),
    KEY idx_cart_items_inventory (inventory_id),
    KEY idx_cart_items_design (custom_design_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_design FOREIGN KEY (custom_design_id) REFERENCES custom_designs (id) ON DELETE CASCADE,
    CONSTRAINT chk_cart_items_quantity CHECK (quantity BETWEEN 1 AND 20)
) ENGINE=InnoDB;

CREATE TABLE coupons (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code             VARCHAR(40)     NOT NULL,
    description      VARCHAR(200)    NULL,
    discount_type    VARCHAR(10)     NOT NULL COMMENT 'PERCENT or FLAT',
    discount_value   DECIMAL(12,2)   NOT NULL,
    min_order_amount DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    max_discount     DECIMAL(12,2)   NULL,
    valid_from       DATETIME(6)     NOT NULL,
    valid_to         DATETIME(6)     NOT NULL,
    usage_limit      INT             NULL COMMENT 'Total uses allowed, null = unlimited',
    per_user_limit   INT             NOT NULL DEFAULT 1,
    used_count       INT             NOT NULL DEFAULT 0,
    active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version          BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupons_code (code),
    KEY idx_coupons_active_validity (active, valid_from, valid_to),
    CONSTRAINT chk_coupons_type CHECK (discount_type IN ('PERCENT', 'FLAT')),
    CONSTRAINT chk_coupons_value CHECK (discount_value > 0 AND (discount_type <> 'PERCENT' OR discount_value <= 100)),
    CONSTRAINT chk_coupons_window CHECK (valid_to > valid_from)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Orders
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_number           VARCHAR(24)     NOT NULL COMMENT 'Public number, e.g. TS-20261102-000123',
    user_id                BIGINT UNSIGNED NOT NULL,
    coupon_id              BIGINT UNSIGNED NULL,
    coupon_code            VARCHAR(40)     NULL COMMENT 'Snapshot',
    order_status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                           COMMENT 'PENDING, CONFIRMED, PROCESSING, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED',
    payment_status         VARCHAR(20)     NOT NULL DEFAULT 'CREATED'
                           COMMENT 'CREATED, PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED',
    payment_method         VARCHAR(20)     NOT NULL COMMENT 'RAZORPAY or COD',
    subtotal               DECIMAL(12,2)   NOT NULL COMMENT 'Sum of base prices x qty',
    customization_total    DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    shipping_charge        DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    discount_total         DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    grand_total            DECIMAL(12,2)   NOT NULL,
    currency               CHAR(3)         NOT NULL DEFAULT 'INR',
    contact_name           VARCHAR(120)    NOT NULL,
    contact_email          VARCHAR(190)    NOT NULL,
    contact_phone          VARCHAR(15)     NOT NULL,
    shipping_address       JSON            NOT NULL COMMENT 'Address snapshot at order time',
    reservation_expires_at DATETIME(6)     NULL,
    placed_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at           DATETIME(6)     NULL,
    shipped_at             DATETIME(6)     NULL,
    delivered_at           DATETIME(6)     NULL,
    cancelled_at           DATETIME(6)     NULL,
    cancel_reason          VARCHAR(300)    NULL,
    courier_name           VARCHAR(80)     NULL,
    tracking_number        VARCHAR(80)     NULL,
    customer_note          VARCHAR(500)    NULL,
    created_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_number (order_number),
    KEY idx_orders_user_created (user_id, created_at),
    KEY idx_orders_status_created (order_status, created_at),
    KEY idx_orders_payment_status (payment_status, created_at),
    KEY idx_orders_reservation_expiry (order_status, payment_status, reservation_expires_at),
    KEY idx_orders_coupon (coupon_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE SET NULL,
    CONSTRAINT chk_orders_status CHECK (order_status IN
        ('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT chk_orders_payment_status CHECK (payment_status IN
        ('CREATED', 'PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT chk_orders_payment_method CHECK (payment_method IN ('RAZORPAY', 'COD')),
    CONSTRAINT chk_orders_amounts CHECK (subtotal >= 0 AND customization_total >= 0 AND shipping_charge >= 0
        AND discount_total >= 0 AND grand_total >= 0),
    CONSTRAINT chk_orders_total CHECK (grand_total = subtotal + customization_total + shipping_charge - discount_total)
) ENGINE=InnoDB;

CREATE TABLE order_items (
    id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id                  BIGINT UNSIGNED NOT NULL,
    product_id                BIGINT UNSIGNED NOT NULL,
    inventory_id              BIGINT UNSIGNED NOT NULL,
    custom_design_id          BIGINT UNSIGNED NULL,
    product_name              VARCHAR(150)    NOT NULL COMMENT 'Snapshot',
    sku                       VARCHAR(64)     NOT NULL COMMENT 'Snapshot',
    size_code                 VARCHAR(10)     NOT NULL,
    color_code                VARCHAR(20)     NOT NULL,
    image_url                 VARCHAR(500)    NULL,
    quantity                  INT             NOT NULL,
    unit_base_price           DECIMAL(12,2)   NOT NULL COMMENT 'Selling price per unit at order time',
    unit_customization_charge DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    line_total                DECIMAL(12,2)   NOT NULL,
    price_breakdown           JSON            NULL COMMENT 'Itemized charges snapshot',
    created_at                DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    KEY idx_order_items_product (product_id),
    KEY idx_order_items_inventory (inventory_id),
    KEY idx_order_items_design (custom_design_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_design FOREIGN KEY (custom_design_id) REFERENCES custom_designs (id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_total CHECK (line_total = (unit_base_price + unit_customization_charge) * quantity)
) ENGINE=InnoDB;

CREATE TABLE order_status_history (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id    BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(20)     NULL,
    to_status   VARCHAR(20)     NOT NULL,
    changed_by  BIGINT UNSIGNED NULL COMMENT 'Null when changed by the system',
    note        VARCHAR(300)    NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_order_status_history_order (order_id, created_at),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_status_history_user FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE coupon_redemptions (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    coupon_id       BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    order_id        BIGINT UNSIGNED NOT NULL,
    discount_amount DECIMAL(12,2)   NOT NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_redemptions_order (order_id),
    KEY idx_coupon_redemptions_coupon_user (coupon_id, user_id),
    CONSTRAINT fk_coupon_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE RESTRICT,
    CONSTRAINT fk_coupon_redemptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_coupon_redemptions_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Time-boxed stock holds for pending orders.
CREATE TABLE inventory_reservations (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    inventory_id BIGINT UNSIGNED NOT NULL,
    order_id     BIGINT UNSIGNED NOT NULL,
    quantity     INT             NOT NULL,
    status       VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, COMMITTED, RELEASED, EXPIRED',
    expires_at   DATETIME(6)     NOT NULL,
    resolved_at  DATETIME(6)     NULL,
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_inventory_reservations_status_expiry (status, expires_at),
    KEY idx_inventory_reservations_order (order_id),
    KEY idx_inventory_reservations_inventory (inventory_id),
    CONSTRAINT fk_inventory_reservations_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_reservations_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT chk_inventory_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_reservations_status CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED'))
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Payments
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id            BIGINT UNSIGNED NOT NULL,
    provider            VARCHAR(20)     NOT NULL COMMENT 'RAZORPAY or COD',
    attempt_no          INT             NOT NULL DEFAULT 1,
    razorpay_order_id   VARCHAR(50)     NULL,
    razorpay_payment_id VARCHAR(50)     NULL,
    razorpay_signature  VARCHAR(128)    NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'CREATED'
                        COMMENT 'CREATED, PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED',
    method              VARCHAR(30)     NULL COMMENT 'upi, card, netbanking, wallet, emi, cod ... as reported',
    method_detail       VARCHAR(100)    NULL COMMENT 'e.g. VPA handle, card network, bank',
    amount              DECIMAL(12,2)   NOT NULL,
    currency            CHAR(3)         NOT NULL DEFAULT 'INR',
    refunded_amount     DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    failure_code        VARCHAR(80)     NULL,
    failure_reason      VARCHAR(300)    NULL,
    needs_review        BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Amount/currency mismatch or anomaly',
    paid_at             DATETIME(6)     NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_razorpay_order (razorpay_order_id),
    UNIQUE KEY uk_payments_razorpay_payment (razorpay_payment_id),
    UNIQUE KEY uk_payments_order_attempt (order_id, attempt_no),
    KEY idx_payments_status_created (status, created_at),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT chk_payments_provider CHECK (provider IN ('RAZORPAY', 'COD')),
    CONSTRAINT chk_payments_status CHECK (status IN ('CREATED', 'PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT chk_payments_amounts CHECK (amount >= 0 AND refunded_amount >= 0 AND refunded_amount <= amount)
) ENGINE=InnoDB;

CREATE TABLE payment_transactions (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_id        BIGINT UNSIGNED NOT NULL,
    type              VARCHAR(30)     NOT NULL
                      COMMENT 'ORDER_CREATED, VERIFY, AUTHORIZED, CAPTURED, FAILED, REFUND_INITIATED, REFUNDED, COD_COLLECTED',
    gateway_reference VARCHAR(80)     NULL COMMENT 'Razorpay payment / refund id',
    amount            DECIMAL(12,2)   NULL,
    status            VARCHAR(30)     NOT NULL,
    source            VARCHAR(20)     NOT NULL COMMENT 'CLIENT_VERIFY, WEBHOOK, RECONCILE, ADMIN, SYSTEM',
    raw_response      JSON            NULL,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_payment_transactions_payment (payment_id, created_at),
    KEY idx_payment_transactions_reference (gateway_reference),
    CONSTRAINT fk_payment_transactions_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE payment_webhook_events (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id            VARCHAR(64)     NOT NULL COMMENT 'X-Razorpay-Event-Id header',
    event_type          VARCHAR(60)     NOT NULL,
    payment_id          BIGINT UNSIGNED NULL,
    razorpay_order_id   VARCHAR(50)     NULL,
    razorpay_payment_id VARCHAR(50)     NULL,
    payload             JSON            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED, PROCESSED, IGNORED, FAILED',
    received_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at        DATETIME(6)     NULL,
    processing_error    VARCHAR(500)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_webhook_events_event (event_id),
    KEY idx_payment_webhook_events_payment (payment_id),
    KEY idx_payment_webhook_events_rzp_order (razorpay_order_id),
    KEY idx_payment_webhook_events_status (status, received_at),
    CONSTRAINT fk_payment_webhook_events_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Engagement and settings
-- ---------------------------------------------------------------------
CREATE TABLE reviews (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    order_id   BIGINT UNSIGNED NULL COMMENT 'Verified purchase',
    rating     TINYINT         NOT NULL,
    title      VARCHAR(120)    NULL,
    comment    TEXT            NULL,
    approved   BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_user_product (user_id, product_id),
    KEY idx_reviews_product_approved (product_id, approved, created_at),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL,
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB;

CREATE TABLE wishlists (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wishlists_user_product (user_id, product_id),
    KEY idx_wishlists_product (product_id),
    CONSTRAINT fk_wishlists_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlists_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE newsletter_subscribers (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email           VARCHAR(190)    NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    unsubscribed_at DATETIME(6)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_newsletter_subscribers_email (email)
) ENGINE=InnoDB;

CREATE TABLE app_settings (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    setting_key   VARCHAR(80)     NOT NULL,
    setting_value VARCHAR(500)    NOT NULL,
    value_type    VARCHAR(20)     NOT NULL DEFAULT 'STRING' COMMENT 'STRING, INT, DECIMAL, BOOLEAN',
    description   VARCHAR(200)    NULL,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_settings_key (setting_key)
) ENGINE=InnoDB;
