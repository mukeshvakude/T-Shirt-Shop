# Project Progress

Last updated: 2026-11-02 (Phase 1)
Working branch: `DEV`. `main` stays stable and only receives reviewed merges.

## Progress tracker

- [x] Repository Analysis
- [x] Architecture Design
- [ ] Database Design (logical design and ER diagram done in Phase 1; Flyway SQL in Phase 2)
- [ ] Spring Boot Setup
- [ ] MySQL Integration
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
| 2 | Database design and MySQL configuration | Flyway `V1__schema.sql`, seed migrations, MySQL user/DB setup script | Pending |
| 3 | Spring Boot backend setup | Maven wrapper project, `com.shop` packages, profiles, Swagger, global errors, health check | Pending |
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
| B1 | **JDK 21 is not installed.** The machine has Java 17.0.13 | Phase 3 | Install Temurin JDK 21 and set `JAVA_HOME`, or approve a portable JDK 21 inside the user folder |
| B2 | **Maven is not installed** | Phase 3 | None. The project will include the Maven wrapper, which downloads Maven itself |
| B3 | **Node.js and npm are not installed** | Phase 6 | Install Node.js LTS (needed for Angular CLI) |
| B4 | **MySQL 8 is not installed or not on PATH** | Phase 2 testing | Install MySQL 8 Server, or Docker Desktop to run it in a container |
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

## Change log

| Date | Phase | Summary | Commit |
|------|-------|---------|--------|
| 2026-11-02 | 1 | Repository analysis, architecture, database design, roadmap, README, .gitignore, env template | see git log on `DEV` |
