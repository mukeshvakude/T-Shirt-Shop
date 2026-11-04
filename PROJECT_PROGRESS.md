# Project Progress

Last updated: 2026-11-04 (Phase 19)
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
- [x] Product Listing (all products, category and search pages; URL-driven filters, sorting, pagination, facets, wishlist hearts)
- [x] Product Details (colour-aware gallery, size/colour/quantity selection with live availability, size guide, add to bag, buy now, reviews, related products)
- [x] Customer Portal (dashboard with order stats and recent orders, profile, email and password changes, address book with one default, wishlist link)
- [x] Custom T-Shirt Designer (Fabric.js designer, front and back, print areas, artwork upload, text, previews, saved designs, admin view and ZIP download)
- [x] Dynamic Pricing (server-side price engine, live price panel with quantity in the designer, admin pricing page for charges and shipping)
- [x] Shopping Cart (server cart for products and custom designs, guest bag priced by the server and merged on sign-in, size/colour/quantity changes, coupons, shipping, stock checks)
- [x] Checkout (checkout page, server totals, pending online orders with a 15-minute stock hold and expiry job, Cash on Delivery, order confirmation page)
- [x] Razorpay Integration (Razorpay order creation, Standard Checkout in Angular, signature verification, retry, failed attempts; needs real test keys for a live run)
- [x] Payment Verification (signature plus Razorpay fetch, amount/currency/order checks, capture of authorized payments, automatic refunds)
- [x] Webhooks (signed, stored once per event id, retry-safe; reconciliation job every 5 minutes)
- [x] Order Management (order history, details with delivery progress and tracking, customer cancellation with stock return and refunds, admin status API, PDF invoices)
- [x] Admin Dashboard (dashboard, orders with status actions, payments with refunds and review, customers with blocking, contact inbox)
- [x] Notifications (order confirmed, shipped, out for delivery, delivered, cancelled, refund, password reset; shop alerts for new orders and contact messages)
- [x] Testing (91% backend line coverage, browser smoke test, payment test checklist, 0 npm audit findings)
- [x] Deployment (deployment guide, Nginx with HTTPS and security headers, systemd service, backups, monitoring, production start-up checks; the shop is not hosted yet, B11)
- [x] GitHub Final Update (all 19 phases pushed to `DEV`; `main` untouched. Open the pull request DEV → main at https://github.com/mukeshvakude/T-Shirt-Shop/compare/main...DEV and merge after review)

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
| 7 | Product pages | Listing, category and search pages, filters, product details, wishlist, guest bag | Done |
| 8 | Customer portal | Dashboard, profile, email and password change, addresses | Done |
| 9 | Custom T-Shirt designer | Fabric.js designer, uploads with backend validation, save/load designs | Done |
| 10 | Dynamic pricing | Pricing engine, estimate API, admin-editable charges, live price panel | Done |
| 11 | Shopping cart | Server cart, guest cart merge, coupons, mixed normal + custom items | Done |
| 12 | Checkout | Checkout form, totals, pending order, stock reservation and expiry job, COD | Done |
| 13 | Razorpay integration | Create order, Standard Checkout in Angular, retry | Done (verified with a stubbed gateway; live test needs keys, B5) |
| 14 | Payment verification and webhooks | Signature verify, gateway fetch, webhook idempotency, late payment refunds, reconciliation | Done |
| 15 | Order management | History, details, tracking, cancellation, status history, PDF invoice | Done (admin screens follow in Phase 16) |
| 16 | Admin portal | Dashboard metrics, orders, payments, refunds, customers, customization (catalog pages delivered in Phase 5) | Done |
| 17 | Notifications | Order confirmation, status update and password reset emails | Done (sending needs SMTP, B6) |
| 18 | Testing | Backend unit and slice tests, frontend tests, payment test checklist, bug fixes | Done |
| 19 | Production deployment | Prod builds, Nginx + HTTPS, env vars, backups, logging, monitoring, live-mode guide | Done (files and guide; hosting is B11) |

## Blocked / needs owner action

| # | Item | Needed by | Action for owner |
|---|------|-----------|------------------|
| ~~B1~~ | Resolved 2026-11-02: portable Temurin JDK 21.0.13 in `C:\Users\ADMIN\tools`, user `JAVA_HOME` set | - | Java 17 remains first on the system PATH, so build with `JAVA_HOME` (the Maven wrapper does) |
| ~~B2~~ | Maven: the project will use the Maven wrapper | - | None |
| ~~B3~~ | Resolved 2026-11-02: portable Node.js 24.22.2 LTS, on user PATH | - | None |
| ~~B4~~ | Resolved 2026-11-02: portable MySQL 8.4.13 LTS on 127.0.0.1:3306, databases `tshirt_shop` and `tshirt_shop_test`, user `tshirt_app` | - | Not a Windows service. For autostart at logon, copy `C:\Users\ADMIN\tools\start-mysql-hidden.vbs` into the Startup folder (Win+R, then `shell:startup`). Otherwise run `start-mysql.cmd` after a reboot |
| B5 | Razorpay test keys | Now (Phase 13 is built) | Create test-mode Key ID, Key Secret and Webhook Secret in the Razorpay Dashboard and put them in the local `.env` (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`), then restart the backend. Never paste the secret into chat or commit it |
| B10 | Settlement bank account | Before live payments | Razorpay pays out to the bank account verified in the Razorpay Dashboard (Account & Settings, Bank account details), not to an address set in the code. On 2026-11-03 the owner named a PhonePe UPI ID for payouts: add the bank account linked to that UPI ID in the dashboard during KYC. Test-mode payments never move real money |
| B6 | SMTP credentials | Now (Phase 17 is built) | Set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` and `MAIL_FROM` in `.env` (for example a Gmail app password, Zoho Mail, Amazon SES or Brevo SMTP), then restart. Until then emails are only logged as "not sent". Set `BUSINESS_EMAIL` to receive new-order and contact alerts |
| B9 | Admin login email | Before launch | The local `.env` uses `admin@tshirtshop.local` with a generated password (copy kept in `C:\Users\ADMIN\tools\mysql-credentials.txt`). For production, set your real address and a new password |
| B7 | Cloudinary account (optional) | Not needed for launch | Local storage under `/var/lib/tshirt-shop/uploads` is backed up nightly by `deploy/backup.sh`. Cloud storage is only worth it with several servers |
| B11 | Server, domain and go-live | When you are ready to launch | Rent a Linux server (VPS), point your domain at it, then follow [DEPLOYMENT.md](DEPLOYMENT.md): live Razorpay keys after KYC, webhook registration, SMTP, business details, backups and the go-live checklist |
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
- Links to pages from later phases (designer, cart, account) open a clearly worded "coming soon" page.
- Listing state (filters, sort, page) lives in the URL, so links, refresh and the back button work. 12 products per page.
- Filter options come from `GET /api/products/facets`: only sizes and colours with stock, price range and category counts.
- "Add to bag" and "Buy now" fill a guest bag stored in the browser (`localStorage`), shown as a count in the header. Prices there are for display only; Phase 11 adds the server cart, merges this bag on sign-in, and the server recalculates every price.
- Wishlist needs sign-in (hearts on cards and product pages, `/wishlist` page). Hidden products drop out of the list but stay saved.
- Customer-facing quantities are capped at the per-item limit, so exact stock is never shown; "Only N left" appears at or below the low-stock threshold.
- Sample product images are generated illustrations (`frontend/scripts/generate-sample-images.mjs`), not photos.
- Dev profile enables Flyway `out-of-order`, because dev sample data uses versions 900+ and new schema versions arrive after it.
- Customer portal at `/account` (dashboard, orders, profile & security, addresses) inside the storefront layout, with wishlist linked.
- Changing the sign-in email or password requires the current password (10 checks per hour per account). A password change signs out every other device and renews the current session in the same response.
- Changing the email marks it unverified; email verification itself arrives with notifications (Phase 17).
- Up to 20 saved addresses. The first address becomes the default; deleting the default promotes the most recently used one. States are validated against the list of Indian states and union territories.
- The dashboard reads order counts straight from the `orders` table until order management (Phase 15) adds full history, tracking, invoices and cancellation.
- Designer (`/customize`, `/customize/:code`): one Fabric.js canvas edits one side at a time inside its print area; the other side is kept as JSON. Mockups are white PNGs tinted in the browser, so every colour works without extra images.
- One print area per side: Front, Left Chest or Full Front on the front, Back or Full Back on the back. Changing the area rescales the design to fit.
- Guests can design without an account; the work is kept in `localStorage` and restored after sign-in. Uploading artwork and saving need sign-in.
- The server re-validates every save: style, in-stock variant, print areas, fonts, and the canvas JSON (only image and text objects, only the customer's own uploaded images, no external, data, blob or javascript URLs, no clip paths, max 50 elements per side). Prices are not taken from the browser.
- Artwork: PNG or JPG checked by content, max 5 MB and 4000 px (admin-editable in `app_settings`), 40 uploads per hour per customer, up to 100 saved designs per customer.
- The browser renders PNG previews (front always, back when used); the server validates them as images before storing.
- Customer artwork and previews are served from random, unlisted URLs (capability links) with private caching, `noindex` and no referrer, instead of per-request authorisation, so plain `<img>` tags work. Only the owner and admins are given these URLs.
- Designs that are part of an order are locked; opening one offers "Save as new design". Locking is set by checkout in Phase 12.
- Admin `/admin/designs`: all designs, details for the print team, and a ZIP with `design.json`, previews and the original artwork.
- Custom T-Shirt price = selling price of the blank T-Shirt for the chosen style + active charges: style, each print area used, custom text (once per design), premium print, size and colour. Missing or inactive charges count as zero.
- Prices include GST ("Inclusive of all taxes"); no separate tax line. Amounts are rounded to paise.
- The browser only sends choices (style, colour, size, print areas used, whether there is text, premium, quantity) to `POST /api/pricing/design-estimate`; it never sends prices. Requests are debounced and a newer one cancels an older one.
- Print charges apply only to sides that carry a design. Quantity is limited by `cart.max_quantity_per_item` (20).
- The shipping estimate in the designer is for that item alone; the cart and checkout (Phases 11 and 12) will calculate shipping for the whole order.
- Saved designs return their current price (`price` in `GET /api/designs/{code}`), so a price change applies to designs saved earlier. Orders will store a price snapshot (`order_items.price_breakdown`) in Phase 12.
- Admin `/admin/pricing`: every chargeable choice is listed, also those without a charge; saving creates or updates the charge. Shipping charge and free-shipping threshold are editable there too. Changes apply to the next calculation (the public price list is not cached).
- Cart: signed-in customers have one server cart (`carts`, `cart_items`). Guests keep a bag of SKUs and quantities in the browser; the cart page prices it with `POST /api/cart/quote`, and it is merged into the account cart on sign-in (quantities added and capped, unavailable items skipped with a notice).
- Every cart response is priced from current data: product selling prices, live design prices, stock and the coupon. Nothing price-related is stored in the cart.
- A cart line is a SKU plus an optional saved design. The same design can be in the bag in several sizes; its colour is fixed by the design (change it with "Edit design"). Blank customizable T-Shirts cannot be bought without a design.
- Quantity per line: 1 to `cart.max_quantity_per_item` (20) and never more than available stock (counting other lines with the same SKU). Up to 50 lines per cart.
- Lines that cannot be bought now (out of stock, not enough stock, no longer sold) stay in the bag with a message, are left out of the totals, and block checkout until fixed. The checkout button re-checks the cart on the server first.
- Coupons need sign-in. Checks: active, inside the validity window, total usage limit, per-customer limit (counted from placed orders), minimum order. PERCENT discounts are capped by `max_discount`. If the bag changes so the coupon no longer applies, the code stays with the reason shown and no discount.
- Free shipping is decided on the item subtotal before the coupon discount (same rule as the designer hint).
- Checkout (`/checkout`): contact details, a saved or new delivery address (optionally saved to the address book), payment method and the order summary split into items (base prices), customization charges, shipping, discount and total.
- Placing an order locks the customer's cart row, prices the cart again on the server and compares the total with the one the customer saw (`expectedTotal`). A difference returns PRICE_CHANGED and the page reloads the summary; the sent total is never used as the price.
- Stock is held under SELECT ... FOR UPDATE in SKU id order. Cash on Delivery orders are CONFIRMED with payment PENDING and take the stock at once (reservations COMMITTED). Online orders are PENDING with payment CREATED and hold the stock for `stock.reservation_minutes` (15, admin setting; the `STOCK_RESERVATION_MINUTES` environment value is not used).
- The order stores snapshots: product name, SKU, size, colour, image, unit base price, unit customization charge, line total and a JSON price breakdown (design code, print areas, charges). Contact details and the address are stored as given.
- The coupon is re-checked under a row lock when the order is placed, a `coupon_redemptions` row is written and `used_count` increases. Custom designs in the order are locked (status ORDERED). The cart is emptied when the order is placed; an unpaid online order is paid later from its order page.
- `OrderExpiryJob` runs every minute: expired holds of unpaid online orders are released (payment CANCELLED, the order stays open), and orders still unpaid after `order.unpaid_cancel_hours` (24) are cancelled, giving back the coupon use and unlocking designs not used by another open order.
- COD is offered only when `cod.enabled` and the total is at most `cod.max_order_value` (INR 5000). Online payment is offered only when real Razorpay keys are configured (not the .env.example placeholders); the payment window itself arrives in Phase 13.
- Order numbers look like TS-20261103-702653 (date in India and six random digits).
- Online payment (Phase 13) uses Razorpay Standard Checkout. `POST /api/payments/create-order` creates (or reuses) a Razorpay order whose amount is the order total in paise, set on the server; the browser receives the public Key ID only. The same endpoint is the retry: a failed attempt gets a new Razorpay order, and an ended stock hold is placed again first (OUT_OF_STOCK if it sold out).
- `POST /api/payments/verify` confirms a payment only when HMAC-SHA256(razorpay_order_id + "|" + razorpay_payment_id, Key Secret) matches the signature (constant-time compare). Valid payments commit the stock hold; if the hold had ended the stock is taken then; if an item sold out meanwhile the payment is flagged `needs_review` and the order stays on hold (automatic refund in Phase 14). Verifying the same payment twice is harmless.
- Rejected signatures are logged in `payment_transactions` and do not change the order. Failed attempts reported by Checkout.js are recorded but never mark anything paid.
- The Razorpay API is called with Spring `RestClient` (Basic auth, 5 s connect and 15 s read timeouts) behind a `RazorpayGateway` interface instead of the Razorpay Java SDK: fewer dependencies and a simple stub for tests. Gateway problems return 502 PAYMENT_GATEWAY_ERROR with a friendly message.
- Starting payment is limited to 15 times per hour per customer.
- Phase 14: browser, webhook and reconciliation all go through one `PaymentProcessor` under the order row lock, so a payment is applied once whichever arrives first. The browser path checks the signature and then fetches the payment from Razorpay; amount, currency and Razorpay order must match the attempt (otherwise `needs_review`); authorized payments are captured.
- Automatic full refunds (Razorpay Refunds API, normal speed): a second payment for an order that is already paid, a payment for a cancelled order, and a payment whose items sold out after the hold ended (the order is then cancelled). A refund call that fails flags the payment and is retried by the reconciliation job.
- Webhooks (`POST /api/payments/webhook`, public): HMAC-SHA256 of the raw body with `RAZORPAY_WEBHOOK_SECRET` must match `X-Razorpay-Signature`; events are stored in `payment_webhook_events` by `X-Razorpay-Event-Id` and handled once (PROCESSED or IGNORED); a processing error is stored as FAILED and answered with HTTP 500 so Razorpay delivers it again. Handled: payment.authorized, payment.captured, payment.failed, order.paid, refund.processed, refund.failed.
- Reconciliation job every 5 minutes (first run 2 minutes after start): asks Razorpay about unconfirmed attempts created between 2 minutes and 48 hours ago (batches of 50) and retries failed refunds. It covers lost browser confirmations and webhooks that cannot reach a local development machine.
- If Razorpay cannot be reached while the browser confirms, the customer is told the payment will be confirmed automatically and not to pay again; reconciliation completes it.
- Phase 15: customers can cancel while the order is before `order.customer_cancel_until_status` (PROCESSING), so while PENDING or CONFIRMED. Cancelling returns held and already-taken stock to the shelf, gives back the coupon use and design locks, and refunds paid online orders in full through the Phase 14 refund logic.
- Admin status changes (`PATCH /api/admin/orders/{number}/status`): CONFIRMED to PROCESSING, PROCESSING to SHIPPED (courier and tracking number required), SHIPPED to OUT_FOR_DELIVERY or DELIVERED, OUT_FOR_DELIVERY to DELIVERED; cancelling is possible until shipped. Every change is in the status history with who made it. Delivering a Cash on Delivery order records the cash as collected (a COD payment row and a COD_COLLECTED transaction).
- Invoices are PDFs made with OpenPDF from the order snapshot, numbered INV-<date>-<number> after the order number, available once the order is confirmed. With `BUSINESS_GSTIN` set the title is "Tax invoice", otherwise "Invoice"; seller details come from `BUSINESS_*` settings and empty values are left out. Amounts print as "INR 1,047.00" (the standard PDF fonts have no rupee sign).
- Phase 16 admin portal opens on the dashboard: sales today / 7 / 30 days (orders confirmed or further along, not pending or cancelled), work waiting (to ship, awaiting payment, payments to review, low stock, new messages), new customers, refunds, a 14-day sales chart with a table view, top products and latest orders.
- Admin orders: search by number, name, email or phone; filter by status or "payment needs review"; the order page offers only the allowed next steps (shipping asks for courier and tracking number) and downloads the invoice.
- Admin payments: every attempt with its full gateway and audit log; a paid online payment can be refunded in full with a reason (for returns after delivery); review flags are cleared with an audit entry.
- Admin customers: search, orders and spend, recent orders; blocking an account signs the customer out everywhere and stops sign-in (admins cannot be blocked here).
- Contact inbox: New / Read / Replied / Closed, reply by email link.
- Phase 19 production: the app refuses to start in the `prod` profile with http:// or localhost addresses, a non-Secure refresh cookie, password reset links in the log, API docs on or missing Razorpay keys, and warns about test keys, a missing webhook secret, email, business details and a relative upload folder (never logging values). Checked against a real start: refused with the dev settings, started with HTTPS settings, Swagger answered 401, logs were JSON with no secrets.
- Phase 19 deploy files: Nginx site (HTTPS, HTTP and www redirects, SPA fallback, `/api` and `/media` proxy with forwarding headers overwritten so clients cannot fake their IP, a per-IP limit, a 12 MB body limit, security headers, CSP in report-only mode until a live payment test is clean, actuator hidden except health), a hardened systemd unit, nightly `mysqldump` and uploads backups kept 14 days, and a release build script. The Nginx file was not run through `nginx -t` here (no Nginx on this PC); the guide runs `nginx -t` before every reload.
- Phase 17 emails: order confirmed (Cash on Delivery at placement, online when paid), shipped (courier and tracking), out for delivery, delivered, cancelled (mentions the refund when there is one, so a cancel-with-refund sends one email), refund started (for example a refund after delivery), and password reset links. The shop gets a "new order" and a "contact form" email at `BUSINESS_EMAIL` when set.
- Emails are sent after the database transaction commits, on a small background pool, so a rolled-back action never emails and a slow mail server never slows the shop. Failures are logged and never break the action. Logs mask addresses (n***@example.com) and never contain email content.
- Templates are inline-styled HTML with a plain-text part; all values are HTML-escaped. Amounts use Indian digit grouping (₹1,25,000), also on invoices.
- The mail health check is switched off so an unreachable mail server does not mark the shop as down.
- A personal UPI ID is not used as a payment destination in the code: payments go through Razorpay, which settles to the merchant bank account (B10).
- "First order" coupons such as WELCOME10 rely on the per-customer limit of 1; there is no separate "no previous orders" rule.
- Refresh-token reuse detection now allows a 10-second grace window: if a rotated token is replayed while its successor was never used (page reloaded during a refresh call), the session continues with a new token. Any other replay still revokes all sessions.

## Known issues / technical debt

| # | Item | Status / plan |
|---|------|---------------|
| ~~T1~~ | Placeholder user lookup rejected every login | Resolved in Phase 4: real database accounts |
| ~~T2~~ | Dev log timestamps used the machine time zone | Resolved: the JVM runs in UTC, so logs, database and API agree |
| ~~T3~~ | Integration tests left data in `tshirt_shop_test` | Resolved: `DatabaseCleaner` resets user data before each test. Two back-to-back full runs pass |
| ~~T4~~ | Mermaid diagrams not render-checked | Resolved: all three diagrams render with mermaid-cli 11 |
| T5 | A disabled account keeps a valid access token for up to 15 minutes | Accepted: refresh is blocked immediately. Revisit if instant lockout is needed |
| T6 | Rate limits are in memory per instance | One server is fine; Nginx adds a per-IP API limit (`deploy/nginx-tshirt-shop.conf`). Use a shared store only if scaling out to several servers |
| ~~T7~~ | Password reset links were not emailed | Resolved in Phase 17 (needs SMTP settings, B6) |
| ~~T8~~ | Sample products pointed at `/assets/products/*.jpg` images that did not exist | Resolved in Phase 6: generated sample illustrations for every sample product and colour. Replace with real photos before launch |
| T9 | Text search uses LIKE, which is fine for a small catalog | Switch to the existing MySQL FULLTEXT index if the catalog grows into the thousands |
| T10 | Uploaded images are stored as uploaded (not re-encoded) | Safe because content is validated and served with nosniff from random URLs. Re-encoding/thumbnails can come with Cloudinary in Phase 9 or 19 |
| T11 | Fonts load from Google Fonts (local DNS hiccups showed up as console errors during tests) | After launch (optional): self-host Inter and Playfair Display, and build a subset icon font with only the ~40 icons used. Measured on 2026-11-03: Google serves the icon font at ~0.95 MB; the full self-hostable icon package is ~13 MB unpacked, so copying it as-is would be slower |
| ~~T12~~ | Product cards linked to pages that arrived in Phase 7 | Resolved in Phase 7: listing, search and product pages are live |
| T14 | Guest bag lives only in this browser until Phase 11 | Phase 11 merges it into the server cart on sign-in |
| T13 | A signed-in customer who opens `/admin` lands on the home page without a message | Accepted: customers have no reason to open admin links; revisit if support requests show confusion |
| T16 | Uploaded artwork that is never saved in a design stays on disk | Open. A simple "no design attached" cleanup is **not safe**: deleting a design detaches its images even when another saved design (or an ordered one) still shows the same upload. A cleanup must check the design canvases first. Disk use is small (5 MB per upload, 20 uploads per hour per customer) |
| T17 | Designer fonts load from Google Fonts | Same plan as T11 (optional, after launch) |
| T18 | The blank T-Shirt price per style comes from its custom product, so changing it means editing that product in the admin portal | Fine for now; a "base price" field on the Pricing page can be added if the owner prefers one place |
| ~~T19~~ | `/checkout` showed a "coming soon" page | Resolved in Phase 12 |
| T20 | Online payment is shown as unavailable until real Razorpay test keys are configured | Owner action B5; the payment window is built and tested with a stub |
| ~~T23~~ | Late payments were only flagged for review | Resolved in Phase 14: refunded automatically |
| ~~T24~~ | Webhooks cannot reach a development PC | Production steps are in DEPLOYMENT.md section 8; locally, reconciliation confirms payments within 5 minutes |
| T26 | Invoice seller details are only the shop name until `BUSINESS_*` values (legal name, address, GSTIN, email, phone) are set in `.env` | Owner action before launch; a GST-registered business must set `BUSINESS_GSTIN` |
| T27 | Invoices show GST-inclusive prices without a tax breakdown per rate | Add HSN codes and CGST/SGST/IGST split if the owner's accountant requires it |
| ~~T25~~ | Flagged payments had no admin screen | Resolved in Phase 16 (Payments, "Needs review") |
| T28 | Coupons are managed in the database only (no admin page) | Add an admin coupons page if the owner wants to create offers without SQL |
| ~~T21~~ | Order emails were not sent | Resolved in Phase 17 (needs SMTP settings, B6) |
| ~~T22~~ | Missing true/false fields (for example `makeDefault`) were rejected with 400 | Resolved in Phase 18: missing fields default to false (regression test in `AccountIT`) |
| T15 | Account deletion and data export (DPDP rights) are handled by request through the Contact page | Add self-service deletion/export before or after launch, as the owner prefers |

## Change log

| Date | Phase | Summary | Commit |
|------|-------|---------|--------|
| 2026-11-02 | 1 | Repository analysis, architecture, database design, roadmap, README, .gitignore, env template | `2d6df49` |
| 2026-11-02 | 2 | Flyway schema (31 tables), reference seed, dev sample catalog, MySQL setup, migration verifier (37/37 pass) | `06e2408` |
| 2026-11-02 | 3 | Spring Boot 4.1.2 backend foundation, reference-data API, 30 unit + 11 integration tests passing | `a16cc06` |
| 2026-11-02 | 4 | Authentication and security, test data cleanup, UTC logs; 60 unit + 22 integration tests passing | `3557528` |
| 2026-11-02 | 5 | Product management backend (71 unit + 29 integration tests) | `e554f74` |
| 2026-11-02 | 5 | Angular 22 workspace and admin portal for products, categories, stock and images (24 frontend tests, browser end-to-end run) | `5881050` |
| 2026-11-03 | 6 | Storefront design and home page, static pages, customer auth pages, home/offers/reviews/newsletter/contact APIs (73 unit + 36 integration backend tests, 41 frontend tests, browser runs) | `f1d9575` |
| 2026-11-03 | 7 | Product listing, category and search pages with facets; product detail page; wishlist; guest bag (74 unit + 39 integration backend tests, 63 frontend tests, catalog browser run) | `4011fb8` |
| 2026-11-03 | 8 | Customer portal: dashboard, profile, email and password change, address book (74 unit + 45 integration backend tests, 70 frontend tests, portal browser run) | `1677275` |
| 2026-11-03 | 9 | Custom T-Shirt designer, saved designs, admin designs with ZIP download, back-view mockups (79 unit + 50 integration backend tests, 79 frontend tests, designer browser run) | `e5747ea` |
| 2026-11-03 | 10 | Dynamic pricing: price engine and estimate API, live price panel with quantity, admin pricing page, refresh-token grace window (84 unit + 56 integration backend tests, 81 frontend tests, pricing browser run) | `5a75cab` |
| 2026-11-03 | 11 | Shopping cart: server cart with products and custom designs, guest quote and merge, coupons, shipping, stock checks, bag page, designer "Add to bag" (84 unit + 62 integration backend tests, 85 frontend tests, cart browser run) | `442d403` |
| 2026-11-03 | 12 | Checkout: checkout page, order placement with server totals, stock holds and expiry job, COD, order page (84 unit + 65 integration backend tests, 89 frontend tests, checkout browser run) | `ebf345a` |
| 2026-11-03 | 13 | Razorpay integration: create order, Standard Checkout, signature verification, retry, failure reports, pay-after-hold handling (87 unit + 71 integration backend tests, 92 frontend tests) | `d749622` |
| 2026-11-03 | 14 | Payment verification and webhooks: gateway re-check, capture, signed idempotent webhooks, automatic refunds, reconciliation job (87 unit + 77 integration backend tests, 92 frontend tests, live Razorpay test-mode checks) | `139860f` |
| 2026-11-03 | 15 | Order management: history, tracking, cancellation with stock return and refunds, admin status API, cash collection, PDF invoices (87 unit + 82 integration backend tests, 95 frontend tests, orders browser run) | `d943020` |
| 2026-11-03 | 16 | Admin portal: dashboard, orders, payments and refunds, customers, contact inbox (87 unit + 86 integration backend tests, 96 frontend tests, admin browser run) | `648a93f` |
| 2026-11-04 | 17 | Notifications: order, shipping, cancellation, refund and password reset emails, shop alerts, Indian digit grouping (90 unit + 90 integration backend tests) | `df281e8` |
| 2026-11-04 | 18 | Testing: JaCoCo coverage (91% instructions, 72% branches, 92% lines), Jackson missing-boolean fix, browser smoke test and cleanup scripts, testing guide and payment test checklist (90 unit + 91 integration backend tests, 96 frontend tests, 11/11 smoke steps, 0 npm audit findings) | `e02fe45` |
| 2026-11-04 | 19 | Production deployment: DEPLOYMENT.md, Nginx with HTTPS and security headers, systemd unit, backups, release build, production start-up checks (93 unit + 91 integration backend tests, 96 frontend tests, production build, prod-profile start checked) | `50096a2` |
