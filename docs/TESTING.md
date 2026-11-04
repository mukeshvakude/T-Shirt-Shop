# Testing

How to run every test layer of the shop. All commands are run from the repository root unless noted.

## 1. Backend unit tests (no database)

```bash
cd backend
./mvnw test
```

`*Test.java` classes: money rules, pricing, coupon maths, signature checks, email templates, mappers.

## 2. Backend integration tests (MySQL)

```bash
cd backend
./mvnw verify
```

`*IT.java` classes run with the `test` profile against the `tshirt_shop_test` database (create it once with the
same user as the dev database; Flyway builds the schema). Each test starts from a clean database (`DatabaseCleaner`).

- Razorpay is replaced by a mocked `RazorpayGateway`; the test profile uses fake keys, so no network calls are made.
- Mail is a mocked `JavaMailSender` and notifications run synchronously, so tests can assert on sent emails.
- Scheduled jobs (order expiry, payment reconciliation) are off; tests call them directly.

Covered flows: registration, login, refresh-token rotation and reuse detection, password reset, catalog and search,
cart (guest merge, stock limits), designer uploads and previews, checkout (COD and online), stock holds and expiry,
payment verify (valid, forged, amount mismatch, late payment after expiry), webhooks (signature, duplicates),
reconciliation, cancellation and refunds, admin order status flow, invoices, admin console, notifications.

## 3. Coverage

`./mvnw verify` also writes a JaCoCo report of unit and integration tests together:
`backend/target/site/jacoco/index.html`.

Phase 18 baseline: **91% instructions, 72% branches, 92% lines**.

## 4. Frontend unit tests

```bash
cd frontend
npm test            # Vitest, single run
npm run build       # production build, fails on template or type errors
```

## 5. Browser smoke test (end to end)

Needs the backend on :8080 and `ng serve` on :4200, plus Puppeteer installed in any folder
(it is not a project dependency):

```bash
PUPPETEER_DIR=/path/with/node_modules/puppeteer node e2e/smoke.mjs
node e2e/cleanup.mjs        # removes the e2e+...@example.com accounts and everything they created
```

Steps: home, listing and search, guest bag, register (guest bag kept), designer to bag, Cash on Delivery
checkout, order history, invoice PDF, customer cancel, phone layout (no sideways scrolling), admin portal pages,
and no browser console or server (5xx) errors.

The admin steps read `ADMIN_EMAIL` / `ADMIN_PASSWORD` from `.env`. The cleanup reads `DB_USERNAME` /
`DB_PASSWORD` from `.env` and puts stock, holds and coupon uses back. It only touches accounts matching
`e2e+%@example.com`.

## 6. Payments

Online payments are tested by hand with Razorpay test mode: see [PAYMENT_TEST_CHECKLIST.md](PAYMENT_TEST_CHECKLIST.md).

## 7. Dependency audit

```bash
cd frontend && npm audit
```

Phase 18: 0 known vulnerabilities.
