-- =====================================================================
-- Reference data needed in every environment (dev and prod).
-- No users or secrets here: the first admin account is created at
-- application start-up from ADMIN_EMAIL / ADMIN_PASSWORD env vars.
-- Stock quantities start at 0. The admin sets real stock.
-- =====================================================================

INSERT INTO roles (name, description) VALUES
    ('ROLE_CUSTOMER', 'Shop customer'),
    ('ROLE_ADMIN',    'Shop administrator');

INSERT INTO categories (code, name, slug, description, sort_order) VALUES
    ('TSHIRTS',        'T-Shirts',            't-shirts',            'Everyday and premium T-Shirts',              1),
    ('SHIRTS',         'Shirts',              'shirts',              'Casual and formal shirts',                   2),
    ('PANTS',          'Pants',               'pants',               'Chinos, trousers, joggers and denim',        3),
    ('CUSTOM_TSHIRTS', 'Customized T-Shirts', 'customized-t-shirts', 'Design your own T-Shirt with live preview',  4);

INSERT INTO product_sizes (code, label, size_group, sort_order) VALUES
    ('S',   'Small',             'APPAREL', 1),
    ('M',   'Medium',            'APPAREL', 2),
    ('L',   'Large',             'APPAREL', 3),
    ('XL',  'Extra Large',       'APPAREL', 4),
    ('XXL', 'Double Extra Large','APPAREL', 5),
    ('W28', 'Waist 28',          'WAIST',  11),
    ('W30', 'Waist 30',          'WAIST',  12),
    ('W32', 'Waist 32',          'WAIST',  13),
    ('W34', 'Waist 34',          'WAIST',  14),
    ('W36', 'Waist 36',          'WAIST',  15),
    ('W38', 'Waist 38',          'WAIST',  16);

INSERT INTO product_colors (code, name, hex, sort_order) VALUES
    ('WHITE',    'White',     '#FFFFFF',  1),
    ('BLACK',    'Black',     '#111111',  2),
    ('RED',      'Red',       '#C62828',  3),
    ('BLUE',     'Royal Blue','#1E4FB8',  4),
    ('GREEN',    'Green',     '#2E7D32',  5),
    ('YELLOW',   'Yellow',    '#F9C80E',  6),
    ('NAVY',     'Navy',      '#1B2340',  7),
    ('GREY',     'Grey',      '#8E8E8E',  8),
    ('MAROON',   'Maroon',    '#6D1A2A',  9),
    ('OLIVE',    'Olive',     '#556B2F', 10),
    ('BEIGE',    'Beige',     '#D9C8A9', 11),
    ('SKY_BLUE', 'Sky Blue',  '#8EC5EA', 12),
    ('KHAKI',    'Khaki',     '#B5A27A', 13),
    ('CHARCOAL', 'Charcoal',  '#36454F', 14);

-- ---------------------------------------------------------------------
-- Customization options
-- Print areas are fractions of the mockup canvas: x, y, w, h in 0..1.
-- ---------------------------------------------------------------------
INSERT INTO customization_options (option_type, code, label, metadata, sort_order) VALUES
    ('TSHIRT_TYPE', 'ROUND_NECK', 'Round Neck', JSON_OBJECT('baseProductSlug', 'custom-round-neck-t-shirt', 'mockup', 'round-neck'), 1),
    ('TSHIRT_TYPE', 'V_NECK',     'V-Neck',     JSON_OBJECT('baseProductSlug', 'custom-v-neck-t-shirt',     'mockup', 'v-neck'),     2),
    ('TSHIRT_TYPE', 'POLO',       'Polo',       JSON_OBJECT('baseProductSlug', 'custom-polo-t-shirt',       'mockup', 'polo'),       3),
    ('TSHIRT_TYPE', 'OVERSIZED',  'Oversized',  JSON_OBJECT('baseProductSlug', 'custom-oversized-t-shirt',  'mockup', 'oversized'),  4),

    ('PRINT_PLACEMENT', 'FRONT',      'Front',      JSON_OBJECT('side', 'FRONT', 'x', 0.32, 'y', 0.26, 'w', 0.36, 'h', 0.36, 'excludes', JSON_ARRAY('FULL_FRONT')), 1),
    ('PRINT_PLACEMENT', 'BACK',       'Back',       JSON_OBJECT('side', 'BACK',  'x', 0.32, 'y', 0.22, 'w', 0.36, 'h', 0.36, 'excludes', JSON_ARRAY('FULL_BACK')),  2),
    ('PRINT_PLACEMENT', 'LEFT_CHEST', 'Left Chest', JSON_OBJECT('side', 'FRONT', 'x', 0.56, 'y', 0.25, 'w', 0.14, 'h', 0.14, 'excludes', JSON_ARRAY('FULL_FRONT')), 3),
    ('PRINT_PLACEMENT', 'FULL_FRONT', 'Full Front', JSON_OBJECT('side', 'FRONT', 'x', 0.27, 'y', 0.22, 'w', 0.46, 'h', 0.58, 'excludes', JSON_ARRAY('FRONT', 'LEFT_CHEST')), 4),
    ('PRINT_PLACEMENT', 'FULL_BACK',  'Full Back',  JSON_OBJECT('side', 'BACK',  'x', 0.27, 'y', 0.18, 'w', 0.46, 'h', 0.62, 'excludes', JSON_ARRAY('BACK')),  5),

    ('FONT', 'MONTSERRAT', 'Montserrat',       JSON_OBJECT('family', 'Montserrat'),       1),
    ('FONT', 'PLAYFAIR',   'Playfair Display', JSON_OBJECT('family', 'Playfair Display'), 2),
    ('FONT', 'BEBAS',      'Bebas Neue',       JSON_OBJECT('family', 'Bebas Neue'),       3),
    ('FONT', 'OSWALD',     'Oswald',           JSON_OBJECT('family', 'Oswald'),           4),
    ('FONT', 'PACIFICO',   'Pacifico',         JSON_OBJECT('family', 'Pacifico'),         5),
    ('FONT', 'ROBOTO',     'Roboto',           JSON_OBJECT('family', 'Roboto'),           6),
    ('FONT', 'LOBSTER',    'Lobster',          JSON_OBJECT('family', 'Lobster'),          7),
    ('FONT', 'ANTON',      'Anton',            JSON_OBJECT('family', 'Anton'),            8);

-- ---------------------------------------------------------------------
-- Customization charges (admin-editable). Missing rows mean no charge.
-- Example from the brief: 299 base + FRONT 50 + BACK 50 + TEXT 30 = 429.
-- ---------------------------------------------------------------------
INSERT INTO customization_charges (option_id, charge_type, code, amount, description)
SELECT o.id, 'TSHIRT_TYPE', o.code,
       CASE o.code WHEN 'OVERSIZED' THEN 80.00 WHEN 'POLO' THEN 60.00 ELSE 0.00 END,
       CONCAT(o.label, ' T-Shirt type charge')
FROM customization_options o WHERE o.option_type = 'TSHIRT_TYPE';

INSERT INTO customization_charges (option_id, charge_type, code, amount, description)
SELECT o.id, 'PRINT_PLACEMENT', o.code,
       CASE o.code WHEN 'FRONT' THEN 50.00 WHEN 'BACK' THEN 50.00 WHEN 'LEFT_CHEST' THEN 30.00
                   WHEN 'FULL_FRONT' THEN 90.00 WHEN 'FULL_BACK' THEN 90.00 END,
       CONCAT(o.label, ' print')
FROM customization_options o WHERE o.option_type = 'PRINT_PLACEMENT';

INSERT INTO customization_charges (charge_type, code, amount, description) VALUES
    ('TEXT',   'CUSTOM_TEXT',    30.00, 'Custom text on the design (charged once per design)'),
    ('DESIGN', 'PREMIUM_DESIGN', 100.00, 'Premium design: high-detail artwork or full-colour photo print'),
    ('SIZE',   'XXL',            40.00, 'XXL size charge'),
    ('COLOR',  'WHITE',           0.00, 'Colour charge'),
    ('COLOR',  'BLACK',           0.00, 'Colour charge');

-- ---------------------------------------------------------------------
-- Blank customizable T-Shirts: one base product per T-Shirt type.
-- ---------------------------------------------------------------------
INSERT INTO products (category_id, name, slug, short_description, description, base_price, status, is_customizable, material, fit)
SELECT c.id, v.name, v.slug, v.short_desc, v.descr, 299.00, 'ACTIVE', TRUE, '100% combed cotton, 180 GSM', v.fit
FROM categories c
JOIN (
    SELECT 'Custom Round Neck T-Shirt' AS name, 'custom-round-neck-t-shirt' AS slug, 'Regular' AS fit,
           'Design your own round neck tee' AS short_desc,
           'Soft combed cotton round neck T-Shirt, ready for your artwork and text.' AS descr
    UNION ALL SELECT 'Custom V-Neck T-Shirt', 'custom-v-neck-t-shirt', 'Regular',
           'Design your own V-neck tee',
           'Classic V-neck T-Shirt in combed cotton, ready for your artwork and text.'
    UNION ALL SELECT 'Custom Polo T-Shirt', 'custom-polo-t-shirt', 'Regular',
           'Design your own polo',
           'Pique-knit polo T-Shirt with collar, ready for chest or back prints.'
    UNION ALL SELECT 'Custom Oversized T-Shirt', 'custom-oversized-t-shirt', 'Oversized',
           'Design your own oversized tee',
           'Drop-shoulder oversized T-Shirt in heavyweight cotton for bold prints.'
) v
WHERE c.code = 'CUSTOM_TSHIRTS';

-- Variants: every apparel size x designer colour, stock 0 until the admin sets it.
INSERT INTO inventory (product_id, size_id, color_id, sku, quantity_on_hand)
SELECT p.id, s.id, col.id,
       CONCAT('CT-', LPAD(p.id, 5, '0'), '-', col.code, '-', s.code), 0
FROM products p
JOIN categories c ON c.id = p.category_id AND c.code = 'CUSTOM_TSHIRTS'
CROSS JOIN product_sizes s
CROSS JOIN product_colors col
WHERE s.size_group = 'APPAREL'
  AND col.code IN ('WHITE', 'BLACK', 'RED', 'BLUE', 'GREEN', 'YELLOW', 'NAVY', 'GREY');

-- ---------------------------------------------------------------------
-- Application settings
-- ---------------------------------------------------------------------
INSERT INTO app_settings (setting_key, setting_value, value_type, description) VALUES
    ('shipping.flat_charge',              '49.00',   'DECIMAL', 'Shipping charge below the free-shipping threshold (INR)'),
    ('shipping.free_threshold',           '999.00',  'DECIMAL', 'Order value at or above which shipping is free (INR)'),
    ('stock.reservation_minutes',         '15',      'INT',     'Minutes stock is held for an unpaid online order'),
    ('order.unpaid_cancel_hours',         '24',      'INT',     'Hours after which an unpaid online order is auto-cancelled'),
    ('order.customer_cancel_until_status','PROCESSING','STRING','Customers may cancel while the order is before this status'),
    ('cod.enabled',                       'true',    'BOOLEAN', 'Allow Cash on Delivery'),
    ('cod.max_order_value',               '5000.00', 'DECIMAL', 'Maximum order value allowed for COD (INR)'),
    ('design.upload_max_bytes',           '5242880', 'INT',     'Maximum design upload size in bytes (5 MB)'),
    ('design.upload_max_dimension_px',    '6000',    'INT',     'Maximum width or height of an uploaded design image'),
    ('cart.max_quantity_per_item',        '20',      'INT',     'Maximum quantity per cart line');
