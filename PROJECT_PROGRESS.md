# Project Progress

Last updated: 2026-11-02 (Phase 3)
Working branch: `DEV`. `main` stays stable and only receives reviewed merges.

## Progress tracker

- [x] Repository Analysis
- [x] Architecture Design
- [x] Database Design (schema, ER diagram, Flyway migrations, seed data, 37 constraint checks passing)
- [x] Spring Boot Setup (Spring Boot 4.1.2, Java 21, profiles, security baseline, errors, Swagger, logging, health)
- [x] MySQL Integration (Flyway on start-up, Hibernate schema validation, integration tests on tshirt_shop_test)
- [ ] Authentication
- [ ] Product Management
- [ ] Angular Homepage
- [ ] Product Listing
- [ ] Product Details
- [ ] Customer Portal
- [ ] Custom T-Shirt Designer
- [ ] Dynamic Pricing
- [ ] Shopping Cart
- [ ] Checkout
- [ ] Razorpay Integration
- [ ] Payment Verification
- [ ] Webhooks
- [ ] Order Management
- [ ] Admin Dashboard
- [ ] Notifications
- [ ] Testing
- [ ] Deployment
- [ ] GitHub Final Update

## Phase 1 findings: existing repository

Inspected on 2026-11-02 from https://github.com/mukeshvakude/T-Shirt-Shop.git

| Item | Finding |
|------|---------|
| Branches | `main` and `DEV`, both at commit `963ad82` "Initial commit" |
| Files | Only `README.md` containing the title `# T-Shirt-Shop` |
| Frontend | None |
| Backend | None |
| Database config | None |
| APIs | None |
| Dependencies | None |
| Bugs / technical debt | None, because there is no code. Nothing needed preserving |

Conclusion: the project starts from a clean slate. All modules in the brief are missing.

## Roadmap

The brief contains two phase lists. The 19-phase "Strict step-by-step execution" list is the one followed here.

| # | Phase | Main deliverables | Status |
|---|-------|-------------------|--------|
| 1 | Repository analysis and architecture | Analysis, architecture doc, ER diagram, roadmap, README, .gitignore, env template | Done |
| 2 | Database design and MySQL configuration | Flyway `V1__create_schema.sql`, `V2__seed_reference_data.sql`, dev `V900` sample catalog, MySQL setup script, verifier | Done |
| 3 | Spring Boot backend setup | Maven wrapper project, `com.shop` packages, profiles, Swagger, global errors, health check, reference-data API | Done |
| 4 | Authentication and security | Register, login, refresh, logout, forgot/reset password, JWT, BCrypt, roles, rate limit | Pending |
| 5 | Product management | Category/product CRUD, images, variants, inventory, search, filters, sort, pagination | Pending |
| 6 | Angular website design and homepage | Angular workspace, theme, layout shells, homepage sections, static pages | Pending |
| 7 | Product pages | Listing, category pages, filters, product details, wishlist | Pending |
| 8 | Customer portal | Dashboard, profile, password, addresses | Pending |
| 9 | Custom T-Shirt designer | Fabric.js designer, uploads with backend validation, save/load designs | Pending |
| 10 | Dynamic pricing | Pricing engine, estimate API, admin-editable charges, live price panel | Pending |
| 11 | Shopping cart | Server cart, guest cart merge, coupons, mixed normal + custom items | Pending |
| 12 | Checkout | Checkout form, totals, pending order, stock reservation and expiry job, COD | Pending |
| 13 | Razorpay integration | Create order, Standard Checkout in Angular, retry | Pending |
| 14 | Payment verification and webhooks | Signature verify, gateway fetch, webhook idempotency, late payment refunds, reconciliation | Pending |
| 15 | Order management | History, details, tracking, cancellation, status history, PDF invoice | Pending |
| 16 | Admin portal | Dashboard metrics, products, orders, payments, refunds, customers, customization | Pending |
| 17 | Notifications | Order confirmation, status update and password reset emails | Pending |
| 18 | Testing | Backend unit and slice tests, frontend tests, payment test checklist, bug fixes | Pending |
| 19 | Production deployment | Prod builds, Nginx + HTTPS, env vars, backups, logging, monitoring, live-mode guide | Pending |

## Blocked / needs owner action

| # | Item | Needed by | Action for owner |
|---|------|-----------|------------------|
| ~~B1~~ | Resolved 2026-11-02: portable Temurin JDK 21.0.13 in `C:\Users\ADMIN\tools`, user `JAVA_HOME` set | - | Java 17 remains first on the system PATH, so build with `JAVA_HOME` (the Maven wrapper does) |
| ~~B2~~ | Maven: the project will use the Maven wrapper | - | None |
| ~~B3~~ | Resolved 2026-11-02: portable Node.js 24.22.2 LTS, on user PATH | - | None |
| ~~B4~~ | Resolved 2026-11-02: portable MySQL 8.4.13 LTS on 127.0.0.1:3306, databases `tshirt_shop` and `tshirt_shop_test`, user `tshirt_app` | - | Start it after a reboot with `C:\Users\ADMIN\tools\start-mysql.cmd`. It is not a Windows service |
| B5 | Razorpay test keys | Phase 13 | Create test-mode Key ID, Key Secret and Webhook Secret, and put them in a local `.env`. Never paste the secret into chat or commit it |
| B6 | SMTP credentials | Phase 17 | Provide a mail account (for example Gmail app password or a transactional provider) via `.env` |
| B7 | Cloudinary account (optional) | Phase 9 | Only if cloud storage is preferred over secure local storage |
| B8 | Business content | Phase 6 | Brand name, logo, real product photos, contact details, policy texts. Placeholders are used until provided |

## Decisions made with sensible defaults

- Angular Material (not Bootstrap) for the UI component library.
- Secure local image storage by default, with a Cloudinary implementation selectable by configuration.
- Stock reservation of 15 minutes for unpaid online orders, then automatic release. Late payments are re-reserved or auto-refunded.
- Shipping: flat INR 49, free above INR 999. Admin can change both.
- COD orders are confirmed immediately with payment status `PENDING` until cash is collected.
- Each T-Shirt type (Round Neck, V-Neck, Polo, Oversized) is its own blank base product, so stock is tracked per type, size and colour.
- Polo carries a INR 60 type charge and XXL a INR 40 size charge. Both were not in the brief and are editable by the admin.
- COD is limited to orders up to INR 5000. Admin-editable.
- Enum-like columns are VARCHAR with CHECK constraints, so adding a value is a small migration.
- Spring Boot 4.1.2 is used: the newest GA release on 2026-11-02, compatible with Java 21.
- Errors use the RFC 7807 problem format with a stable `code` field the Angular app can rely on.
- Unit tests (`*Test`) need no database. Integration tests (`*IT`) run on `mvnw verify` against `tshirt_shop_test`. Docker is not installed, so Testcontainers is not used.
- Virtual threads are enabled for request handling (Java 21).

## Known issues / technical debt

| # | Item | Plan |
|---|------|------|
| T1 | `SecurityConfig` has a placeholder user lookup that rejects every login | Replaced by the database-backed user service in Phase 4 |
| T2 | Dev log timestamps use the machine time zone (IST). Database and API times are UTC | Acceptable for dev. Production servers run in UTC and log JSON |

## Change log

| Date | Phase | Summary | Commit |
|------|-------|---------|--------|
| 2026-11-02 | 1 | Repository analysis, architecture, database design, roadmap, README, .gitignore, env template | `2d6df49` |
| 2026-11-02 | 2 | Flyway schema (31 tables), reference seed, dev sample catalog, MySQL setup, migration verifier (37/37 pass) | `06e2408` |
| 2026-11-02 | 3 | Spring Boot 4.1.2 backend foundation, reference-data API, 30 unit + 11 integration tests passing | see git log on `DEV` |
