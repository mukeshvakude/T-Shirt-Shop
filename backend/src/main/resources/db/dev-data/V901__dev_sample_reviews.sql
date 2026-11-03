-- =====================================================================
-- DEVELOPMENT ONLY: sample reviewers and approved reviews so the
-- storefront "Customer reviews" section can be developed and tested.
-- Never loaded in production, where only real customer reviews appear.
-- The sample accounts are disabled and have an unusable password hash,
-- so nobody can log in with them.
-- =====================================================================

INSERT INTO users (email, password_hash, full_name, enabled, email_verified) VALUES
    ('dev.reviewer1@example.com', '!dev-sample-no-login', 'Ananya Sharma', FALSE, TRUE),
    ('dev.reviewer2@example.com', '!dev-sample-no-login', 'Rohan Mehta',   FALSE, TRUE),
    ('dev.reviewer3@example.com', '!dev-sample-no-login', 'Priya Nair',    FALSE, TRUE),
    ('dev.reviewer4@example.com', '!dev-sample-no-login', 'Karan Singh',   FALSE, TRUE),
    ('dev.reviewer5@example.com', '!dev-sample-no-login', 'Meera Iyer',    FALSE, TRUE);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'ROLE_CUSTOMER'
WHERE u.email LIKE 'dev.reviewer%@example.com';

INSERT INTO reviews (user_id, product_id, rating, title, comment, approved)
SELECT u.id, p.id, v.rating, v.title, v.comment, v.approved
FROM (
    SELECT 'dev.reviewer1@example.com' AS email, 'essential-crew-neck-tee' AS slug, 5 AS rating,
           'My new everyday tee' AS title,
           'Thick fabric that still breathes. Washed it five times and it keeps its shape.' AS comment, TRUE AS approved
    UNION ALL SELECT 'dev.reviewer2@example.com', 'oxford-button-down-shirt', 5, 'Sharp and comfortable',
           'Fits true to size. The collar stays crisp through a full day at the office.', TRUE
    UNION ALL SELECT 'dev.reviewer3@example.com', 'stretch-slim-chinos', 4, 'Great stretch',
           'Very comfortable for long commutes. I would love one more colour option.', TRUE
    UNION ALL SELECT 'dev.reviewer4@example.com', 'oversized-street-tee', 5, 'Perfect oversized fit',
           'Heavy cotton with a proper drop shoulder. Exactly the look I wanted.', TRUE
    UNION ALL SELECT 'dev.reviewer5@example.com', 'custom-round-neck-t-shirt', 5, 'Print came out beautifully',
           'Designed a tee for my brother''s birthday. Colours were vivid and the print feels soft.', TRUE
    UNION ALL SELECT 'dev.reviewer1@example.com', 'pique-polo-tee', 4, 'Smart polo',
           'Nice pique texture and the collar does not curl. Slightly long in the body for me.', TRUE
    UNION ALL SELECT 'dev.reviewer2@example.com', 'everyday-joggers', 3, 'Comfortable',
           'Soft inside, but the waistband could be firmer.', TRUE
    UNION ALL SELECT 'dev.reviewer3@example.com', 'linen-blend-resort-shirt', 2, 'Not approved yet',
           'This review is waiting for moderation and must not appear on the site.', FALSE
) v
JOIN users u ON u.email = v.email
JOIN products p ON p.slug = v.slug;

-- Keep product rating summaries consistent with approved reviews.
UPDATE products p
JOIN (SELECT product_id, ROUND(AVG(rating), 2) AS avg_rating, COUNT(*) AS review_count
      FROM reviews WHERE approved = TRUE GROUP BY product_id) r ON r.product_id = p.id
SET p.avg_rating = r.avg_rating, p.review_count = r.review_count;

-- Advertise the sample coupons.
UPDATE coupons SET is_public = TRUE WHERE code IN ('WELCOME10', 'FLAT100');
