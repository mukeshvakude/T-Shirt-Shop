# Project Progress

Last updated: 2026-11-03 (Phase 6)
Working branch: `DEV`. `main` stays stable and only receives reviewed merges.

## Progress tracker

- [x] Repository Analysis
- [x] Architecture Design
- [x] Database Design (schema, ER diagram, Flyway migrations, seed data, 37 constraint checks passing)
- [x] Spring Boot Setup (Spring Boot 4.1.2, Java 21, profiles, security baseline, errors, Swagger, logging, health)
- [x] MySQL Integration (Flyway on start-up, Hibernate schema validation, integration tests on tshirt_shop_test)
- [x] Authentication (JWT access + rotating refresh cookie, BCrypt, roles, lockout, rate limits, password reset, first admin)
- [x] Product Management (catalog APIs, admin APIs, image upload, stock; admin portal for products, categories and low stock)
- [x] Angular Homepage (storefront shell, home page with all brief sections, static pages, customer sign-in/registration/password reset)
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
| 4 | Authentication and security | Register, login, refresh, logout, forgot/reset password, JWT, BCrypt, roles, rate limit, admin bootstrap | Done |
| 5 | Product management | Category/product CRUD, images, variants, inventory, search, filters, sort, pagination; Angular workspace and admin portal for catalog | Done |
| 6 | Angular website design and homepage | Storefront shell, homepage sections, about/contact/policy pages, customer auth pages, home and engagement APIs, sample product illustrations | Done |
| 7 | Product pages | Listing, category pages, filters, product details, wishlist | Pending |
| 8 | Customer portal | Dashboard, profile, password, addresses | Pending |
| 9 | Custom T-Shirt designer | Fabric.js designer, uploads with backend validation, save/load designs | Pending |
| 10 | Dynamic pricing | Pricing engine, estimate API, admin-editable charges, live price panel | Pending |
| 11 | Shopping cart | Server cart, guest cart merge, coupons, mixed normal + custom items | Pending |
| 12 | Checkout | Checkout form, totals, pending order, stock reservation and expiry job, COD | Pending |
| 13 | Razorpay integration | Create order, Standard Checkout in Angular, retry | Pending |
| 14 | Payment verification and webhooks | Signature verify, gateway fetch, webhook idempotency, late payment refunds, reconciliation | Pending |
| 15 | Order management | History, details, tracking, cancellation, status history, PDF invoice | Pending |
| 16 | Admin portal | Dashboard metrics, orders, payments, refunds, customers, customization (catalog pages delivered in Phase 5) | Pending |
| 17 | Notifications | Order confirmation, status update and password reset emails | Pending |
| 18 | Testing | Backend unit and slice tests, frontend tests, payment test checklist, bug fixes | Pending |
| 19 | Production deployment | Prod builds, Nginx + HTTPS, env vars, backups, logging, monitoring, live-mode guide | Pending |

## Blocked / needs owner action

| # | Item | Needed by | Action for owner |
|---|------|-----------|------------------|
| ~~B1~~ | Resolved 2026-11-02: portable Temurin JDK 21.0.13 in `C:\Users\ADMIN\tools`, user `JAVA_HOME` set | - | Java 17 remains first on the system PATH, so build with `JAVA_HOME` (the Maven wrapper does) |
| ~~B2~~ | Maven: the project will use the Maven wrapper | - | None |
| ~~B3~~ | Resolved 2026-11-02: portable Node.js 24.22.2 LTS, on user PATH | - | None |
| ~~B4~~ | Resolved 2026-11-02: portable MySQL 8.4.13 LTS on 127.0.0.1:3306, databases `tshirt_shop` and `tshirt_shop_test`, user `tshirt_app` | - | Not a Windows service. For autostart at logon, copy `C:\Users\ADMIN\tools\start-mysql-hidden.vbs` into the Startup folder (Win+R, then `shell:startup`). Otherwise run `start-mysql.cmd` after a reboot |
| B5 | Razorpay test keys | Phase 13 | Create test-mode Key ID, Key Secret and Webhook Secret, and put them in a local `.env`. Never paste the secret into chat or commit it |
| B6 | SMTP credentials | Phase 17 | Provide a mail account (for example Gmail app password or a transactional provider) via `.env`. Until then password reset links are only logged (dev) |
| B9 | Admin login email | Before launch | The local `.env` uses `admin@tshirtshop.local` with a generated password (copy kept in `C:\Users\ADMIN\tools\mysql-credentials.txt`). For production, set your real address and a new password |
| B7 | Cloudinary account (optional) | Phase 9 | Only if cloud storage is preferred over secure local storage |
| B8 | Business content | Before launch | Fill in `frontend/src/app/core/config/site.config.ts`: brand name, support email and phone, address, business hours, social links (empty values are hidden, nothing is invented). Replace the sample product illustrations with real photos. Have the Privacy, Terms and Returns texts reviewed for your business |

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
- JWTs are validated with Spring Security's built-in resource-server support (HS256 shared secret) instead of a hand-written filter.
- The refresh token is only ever in an HttpOnly, SameSite=Strict cookie, never in the response body or browser storage.
- Customers can register with email alone. Phone is optional at sign-up and required at checkout.
- Email verification is not enforced at login. The `email_verified` flag exists for a later phase.
- The admin portal for catalog management was built in Phase 5 (owner request), ahead of Phase 16. It uses Angular 22.2 with Angular Material, which also starts the Angular workspace planned for Phase 6.
- New products start as DRAFT or INACTIVE. Publishing requires at least one active size and colour with stock records.
- Products that appear in orders cannot be deleted, only archived. Sizes and colours removed from a product are deactivated, never deleted.
- Product images: PNG or JPG, max 5 MB and 4000 x 4000 px, max 12 per product, validated by content on the server.
- Customer-facing stock numbers are capped at the per-item order limit (20), so exact stock levels are not exposed.
- Storefront home page data comes from one endpoint (`GET /api/home`, 60 s public cache) instead of seven separate calls.
- Sections without content are hidden: no reviews, offers or on-sale items means no empty section. Reviews show first name and last initial only.
- Coupons are advertised only when marked public (`coupons.is_public`, migration V3).
- Contact messages are stored in `contact_messages` (admin inbox in Phase 16); a hidden honeypot field silently drops bot submissions.
- One sign-in page serves customers and admins. Return URLs must be same-site paths (no open redirects).
- `POST /api/auth/refresh` without a cookie returns 204, so anonymous visitors do not log errors on every page load.
- Links to pages from later phases (listing, product details, search, designer, cart, account) open a clearly worded "coming soon" page.
- Sample product images are generated illustrations (`frontend/scripts/generate-sample-images.mjs`), not photos.
- Dev profile enables Flyway `out-of-order`, because dev sample data uses versions 900+ and new schema versions arrive after it.

## Known issues / technical debt

| # | Item | Status / plan |
|---|------|---------------|
| ~~T1~~ | Placeholder user lookup rejected every login | Resolved in Phase 4: real database accounts |
| ~~T2~~ | Dev log timestamps used the machine time zone | Resolved: the JVM runs in UTC, so logs, database and API agree |
| ~~T3~~ | Integration tests left data in `tshirt_shop_test` | Resolved: `DatabaseCleaner` resets user data before each test. Two back-to-back full runs pass |
| ~~T4~~ | Mermaid diagrams not render-checked | Resolved: all three diagrams render with mermaid-cli 11 |
| T5 | A disabled account keeps a valid access token for up to 15 minutes | Accepted: refresh is blocked immediately. Revisit if instant lockout is needed |
| T6 | Rate limits are in memory per instance | Fine for one server. Add proxy limits if scaling out (Phase 19) |
| T7 | Password reset links are not emailed yet | Phase 17. Dev profile logs the link |
| ~~T8~~ | Sample products pointed at `/assets/products/*.jpg` images that did not exist | Resolved in Phase 6: generated sample illustrations for every sample product and colour. Replace with real photos before launch |
| T9 | Text search uses LIKE, which is fine for a small catalog | Switch to the existing MySQL FULLTEXT index if the catalog grows into the thousands |
| T10 | Uploaded images are stored as uploaded (not re-encoded) | Safe because content is validated and served with nosniff from random URLs. Re-encoding/thumbnails can come with Cloudinary in Phase 9 or 19 |
| T11 | Fonts load from Google Fonts | Consider self-hosting Inter, Playfair Display and Material Symbols before launch (privacy, speed, no third-party dependency). Phase 19 |
| T12 | Product cards link to pages that arrive in Phase 7 | Phase 7 replaces the "coming soon" pages for listing, details and search |
| T13 | A signed-in customer who opens `/admin` lands on the home page without a message | Acceptable; revisit with the account area in Phase 8 |

## Change log

| Date | Phase | Summary | Commit |
|------|-------|---------|--------|
| 2026-11-02 | 1 | Repository analysis, architecture, database design, roadmap, README, .gitignore, env template | `2d6df49` |
| 2026-11-02 | 2 | Flyway schema (31 tables), reference seed, dev sample catalog, MySQL setup, migration verifier (37/37 pass) | `06e2408` |
| 2026-11-02 | 3 | Spring Boot 4.1.2 backend foundation, reference-data API, 30 unit + 11 integration tests passing | `a16cc06` |
| 2026-11-02 | 4 | Authentication and security, test data cleanup, UTC logs; 60 unit + 22 integration tests passing | `3557528` |
| 2026-11-02 | 5 | Product management backend (71 unit + 29 integration tests) | `e554f74` |
| 2026-11-02 | 5 | Angular 22 workspace and admin portal for products, categories, stock and images (24 frontend tests, browser end-to-end run) | `5881050` |
| 2026-11-03 | 6 | Storefront design and home page, static pages, customer auth pages, home/offers/reviews/newsletter/contact APIs (73 unit + 36 integration backend tests, 41 frontend tests, browser runs) | see git log on `DEV` |
