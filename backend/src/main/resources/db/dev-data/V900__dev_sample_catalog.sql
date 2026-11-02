-- =====================================================================
-- DEVELOPMENT ONLY sample catalog.
-- Loaded only when Flyway locations include classpath:db/dev-data
-- (the dev profile). Never enabled in production.
-- Image paths point to frontend assets added in Phase 6; replace with
-- real product photography before launch.
-- =====================================================================

INSERT INTO products (category_id, name, slug, short_description, description, base_price, discount_price,
                      status, featured, new_arrival, material, fit, care_instructions, sold_count)
SELECT c.id, v.name, v.slug, v.short_desc, v.descr, v.base_price, v.discount_price,
       'ACTIVE', v.featured, v.new_arrival, v.material, v.fit, 'Machine wash cold, inside out. Do not bleach.', v.sold
FROM (
    SELECT 'TSHIRTS' AS cat, 'Essential Crew Neck Tee' AS name, 'essential-crew-neck-tee' AS slug,
           'The everyday premium tee' AS short_desc,
           'Heavyweight 200 GSM combed cotton with a clean crew neck and a tailored regular fit.' AS descr,
           599.00 AS base_price, 449.00 AS discount_price, TRUE AS featured, FALSE AS new_arrival,
           '100% combed cotton' AS material, 'Regular' AS fit, 140 AS sold
    UNION ALL SELECT 'TSHIRTS', 'Monochrome Logo Tee', 'monochrome-logo-tee', 'Minimal chest logo',
           'Soft-touch cotton tee with a subtle tonal chest logo.', 699.00, NULL, TRUE, TRUE,
           '100% cotton', 'Regular', 85
    UNION ALL SELECT 'TSHIRTS', 'Oversized Street Tee', 'oversized-street-tee', 'Drop-shoulder oversized fit',
           'Relaxed drop-shoulder silhouette in 240 GSM cotton for a structured drape.', 899.00, 749.00, FALSE, TRUE,
           '100% cotton, 240 GSM', 'Oversized', 60
    UNION ALL SELECT 'TSHIRTS', 'Classic V-Neck Tee', 'classic-v-neck-tee', 'Clean V-neck staple',
           'Lightweight breathable cotton V-neck for layering or wearing solo.', 549.00, NULL, FALSE, FALSE,
           '100% cotton', 'Slim', 40
    UNION ALL SELECT 'TSHIRTS', 'Pique Polo Tee', 'pique-polo-tee', 'Smart-casual polo',
           'Breathable pique knit with a ribbed collar and two-button placket.', 999.00, 849.00, TRUE, FALSE,
           'Cotton pique', 'Regular', 95
    UNION ALL SELECT 'SHIRTS', 'Oxford Button-Down Shirt', 'oxford-button-down-shirt', 'Crisp everyday Oxford',
           'Textured Oxford cotton with a button-down collar and a single chest pocket.', 1499.00, 1199.00, TRUE, FALSE,
           '100% cotton Oxford', 'Regular', 70
    UNION ALL SELECT 'SHIRTS', 'Linen Blend Resort Shirt', 'linen-blend-resort-shirt', 'Breezy camp collar',
           'Linen-cotton blend with a relaxed camp collar, made for warm days.', 1799.00, NULL, FALSE, TRUE,
           '55% linen, 45% cotton', 'Relaxed', 30
    UNION ALL SELECT 'SHIRTS', 'Slim Fit Formal Shirt', 'slim-fit-formal-shirt', 'Sharp office essential',
           'Wrinkle-resistant poplin with a spread collar and a slim tailored fit.', 1299.00, 999.00, FALSE, FALSE,
           'Cotton poplin', 'Slim', 55
    UNION ALL SELECT 'SHIRTS', 'Denim Overshirt', 'denim-overshirt', 'Layer-ready denim',
           'Washed mid-weight denim overshirt with twin flap pockets.', 1999.00, NULL, FALSE, TRUE,
           '100% cotton denim', 'Regular', 20
    UNION ALL SELECT 'PANTS', 'Stretch Slim Chinos', 'stretch-slim-chinos', 'Comfort stretch chinos',
           'Cotton twill with a touch of stretch for all-day comfort and a slim taper.', 1699.00, 1399.00, TRUE, FALSE,
           '98% cotton, 2% elastane', 'Slim', 65
    UNION ALL SELECT 'PANTS', 'Relaxed Cargo Pants', 'relaxed-cargo-pants', 'Utility cargo fit',
           'Relaxed cargo pants with six pockets and adjustable hems.', 1899.00, NULL, FALSE, TRUE,
           'Cotton ripstop', 'Relaxed', 25
    UNION ALL SELECT 'PANTS', 'Tailored Trousers', 'tailored-trousers', 'Smart pleat-front trousers',
           'Fluid tailored trousers with a single pleat and a straight leg.', 2199.00, 1849.00, FALSE, FALSE,
           'Poly-viscose', 'Straight', 35
    UNION ALL SELECT 'PANTS', 'Everyday Joggers', 'everyday-joggers', 'Soft fleece joggers',
           'Brushed-back fleece joggers with a drawstring waist and zip pockets.', 1199.00, 999.00, FALSE, FALSE,
           'Cotton fleece', 'Regular', 80
) v
JOIN categories c ON c.code = v.cat;

-- Colours offered per sample product.
CREATE TEMPORARY TABLE tmp_product_colors (slug VARCHAR(170), color_code VARCHAR(20));
INSERT INTO tmp_product_colors VALUES
    ('essential-crew-neck-tee', 'WHITE'), ('essential-crew-neck-tee', 'BLACK'), ('essential-crew-neck-tee', 'NAVY'),
    ('monochrome-logo-tee', 'BLACK'), ('monochrome-logo-tee', 'WHITE'),
    ('oversized-street-tee', 'BLACK'), ('oversized-street-tee', 'BEIGE'), ('oversized-street-tee', 'OLIVE'),
    ('classic-v-neck-tee', 'WHITE'), ('classic-v-neck-tee', 'GREY'),
    ('pique-polo-tee', 'NAVY'), ('pique-polo-tee', 'WHITE'), ('pique-polo-tee', 'MAROON'),
    ('oxford-button-down-shirt', 'WHITE'), ('oxford-button-down-shirt', 'SKY_BLUE'),
    ('linen-blend-resort-shirt', 'BEIGE'), ('linen-blend-resort-shirt', 'WHITE'),
    ('slim-fit-formal-shirt', 'WHITE'), ('slim-fit-formal-shirt', 'SKY_BLUE'), ('slim-fit-formal-shirt', 'BLACK'),
    ('denim-overshirt', 'BLUE'),
    ('stretch-slim-chinos', 'KHAKI'), ('stretch-slim-chinos', 'NAVY'), ('stretch-slim-chinos', 'BLACK'),
    ('relaxed-cargo-pants', 'OLIVE'), ('relaxed-cargo-pants', 'BLACK'),
    ('tailored-trousers', 'CHARCOAL'), ('tailored-trousers', 'BLACK'),
    ('everyday-joggers', 'GREY'), ('everyday-joggers', 'BLACK');

-- Variants: tops get apparel sizes, pants get waist sizes. Stock 25 each.
INSERT INTO inventory (product_id, size_id, color_id, sku, quantity_on_hand)
SELECT p.id, s.id, col.id,
       CONCAT(LEFT(c.code, 2), '-', LPAD(p.id, 5, '0'), '-', col.code, '-', s.code), 25
FROM tmp_product_colors t
JOIN products p        ON p.slug = t.slug
JOIN categories c      ON c.id = p.category_id
JOIN product_colors col ON col.code = t.color_code
JOIN product_sizes s   ON s.size_group = IF(c.code = 'PANTS', 'WAIST', 'APPAREL');

-- One primary image per colour plus a detail shot per product.
INSERT INTO product_images (product_id, color_id, image_url, alt_text, sort_order, is_primary)
SELECT p.id, col.id,
       CONCAT('/assets/products/', p.slug, '-', LOWER(col.code), '.jpg'),
       CONCAT(p.name, ' in ', col.name),
       ROW_NUMBER() OVER (PARTITION BY p.id ORDER BY col.sort_order) - 1,
       ROW_NUMBER() OVER (PARTITION BY p.id ORDER BY col.sort_order) = 1
FROM tmp_product_colors t
JOIN products p         ON p.slug = t.slug
JOIN product_colors col ON col.code = t.color_code;

INSERT INTO product_images (product_id, image_url, alt_text, sort_order, is_primary)
SELECT p.id, CONCAT('/assets/products/', p.slug, '-detail.jpg'), CONCAT(p.name, ' fabric detail'), 99, FALSE
FROM products p JOIN tmp_product_colors t ON t.slug = p.slug
GROUP BY p.id, p.slug, p.name;

DROP TEMPORARY TABLE tmp_product_colors;

-- Stock and images for the blank customizable T-Shirts.
UPDATE inventory i
JOIN products p   ON p.id = i.product_id
JOIN categories c ON c.id = p.category_id AND c.code = 'CUSTOM_TSHIRTS'
SET i.quantity_on_hand = 50;

INSERT INTO product_images (product_id, image_url, alt_text, sort_order, is_primary)
SELECT p.id, CONCAT('/assets/mockups/', JSON_UNQUOTE(JSON_EXTRACT(o.metadata, '$.mockup')), '-white-front.png'),
       CONCAT(p.name, ' blank'), 0, TRUE
FROM products p
JOIN customization_options o
  ON o.option_type = 'TSHIRT_TYPE'
 AND JSON_UNQUOTE(JSON_EXTRACT(o.metadata, '$.baseProductSlug')) = p.slug;

-- Sample coupons.
INSERT INTO coupons (code, description, discount_type, discount_value, min_order_amount, max_discount,
                     valid_from, valid_to, usage_limit, per_user_limit) VALUES
    ('WELCOME10', '10% off your first order (max INR 200)', 'PERCENT', 10.00, 499.00, 200.00,
     '2026-01-01 00:00:00', '2027-12-31 23:59:59', NULL, 1),
    ('FLAT100',   'INR 100 off orders above INR 999',      'FLAT',   100.00, 999.00, NULL,
     '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1000, 3);
