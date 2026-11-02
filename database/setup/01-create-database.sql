-- One-time MySQL setup for the T-Shirt Shop.
-- Run as an administrative MySQL user, after replacing the password placeholder:
--   mysql -u root -p < database/setup/01-create-database.sql
-- Tables are NOT created here. Flyway creates them on backend start-up.

CREATE DATABASE IF NOT EXISTS tshirt_shop
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Optional database for integration tests.
CREATE DATABASE IF NOT EXISTS tshirt_shop_test
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'tshirt_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_STRONG_PASSWORD';

-- Development: full rights so Flyway can create and alter tables.
GRANT ALL PRIVILEGES ON tshirt_shop.*      TO 'tshirt_app'@'localhost';
GRANT ALL PRIVILEGES ON tshirt_shop_test.* TO 'tshirt_app'@'localhost';

-- Production: prefer a separate migration user for Flyway and a runtime user with only
--   SELECT, INSERT, UPDATE, DELETE ON tshirt_shop.*   (see docs/DEPLOYMENT.md in Phase 19)

FLUSH PRIVILEGES;
