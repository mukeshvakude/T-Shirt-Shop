# T-Shirt-Shop

A full-stack clothing e-commerce platform for T-Shirts, Shirts, Pants and
Customized T-Shirts. It includes a storefront, a customer account portal, an
admin portal, a Fabric.js T-Shirt designer with live pricing, and Razorpay
online payments alongside Cash on Delivery.

> **Status:** Phases 1 to 15 of 19 complete (architecture, database, backend
> foundation, authentication, product management with the admin portal, storefront
> home page, product listing and details, customer portal, custom T-Shirt designer,
> dynamic pricing, shopping cart, checkout with Cash on Delivery, Razorpay payments
> with verification, webhooks, automatic refunds and reconciliation, order management with
> tracking, cancellation and PDF invoices). See [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md).

## Technology stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular, TypeScript, SCSS, Angular Material, RxJS, Reactive Forms, Fabric.js |
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA / Hibernate, JWT, Maven |
| Database | MySQL 8 with Flyway migrations |
| Payments | Razorpay Standard Checkout, Razorpay Java SDK, Razorpay webhooks |
| Images | Secure local storage (default) or Cloudinary |
| Tooling | Git, GitHub, Postman, Swagger/OpenAPI, JUnit 5, Mockito |

## Repository layout

```
T-Shirt-Shop/
├── backend/              # Spring Boot API (Phase 3+)
├── frontend/             # Angular app (Phase 6+)
├── database/             # MySQL setup script, server settings, migration verifier
├── docs/
│   ├── ARCHITECTURE.md   # system design, payment flow, stock reservation, pricing
│   ├── DATABASE_DESIGN.md# tables and Mermaid ER diagram
│   └── GIT_WORKFLOW.md   # branching, commits, secret hygiene
├── .env.example          # all environment variables, placeholders only
├── PROJECT_PROGRESS.md   # checklist, roadmap, blockers
└── README.md
```

## Prerequisites

- JDK 21
- Node.js LTS and npm (Angular CLI is installed per project)
- MySQL 8
- Git
- A Razorpay account in Test Mode

Maven does not need a separate install. The backend ships the Maven wrapper.

## Installation

```bash
git clone https://github.com/mukeshvakude/T-Shirt-Shop.git
cd T-Shirt-Shop
git checkout DEV
cp .env.example .env   # then fill in real local values
```

## Database configuration

1. Edit the password placeholder in `database/setup/01-create-database.sql`, then run it as a MySQL admin:

   ```bash
   mysql -u root -p < database/setup/01-create-database.sql
   ```

2. Put the same password in `.env` as `DB_PASSWORD`.
3. Recommended server settings are in `database/setup/my.cnf.example`. Use UTF-8 (`utf8mb4`) and UTC.

Flyway creates and updates all tables when the backend starts. Migrations live in
`backend/src/main/resources/db/migration`. Development sample data lives in
`backend/src/main/resources/db/dev-data` and only loads with the `dev` profile.

To check the migrations and constraints against a scratch database:

```bash
MYSQL_PWD=<root password> ./database/verify-migrations.sh mysql root
```

The schema overview and ER diagram are in [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md).

## Backend execution

Requires JDK 21 (`JAVA_HOME`) and a running MySQL with the `.env` values filled in.

```bash
cd backend
./mvnw spring-boot:run      # dev profile by default; Windows: mvnw.cmd spring-boot:run
./mvnw verify               # unit tests + integration tests against MySQL
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

More detail, including profiles and the error format, is in [backend/README.md](backend/README.md).

## Frontend execution

Requires Node.js 24 LTS. Start the backend first.

```bash
cd frontend
npm install
npm start   # http://localhost:4200 (proxies /api and /media to the backend)
```

- Storefront: http://localhost:4200
- T-Shirt designer: http://localhost:4200/customize
- Shopping bag: http://localhost:4200/cart
- Checkout: http://localhost:4200/checkout (sign-in required)
- Admin portal: http://localhost:4200/admin (sign in with `ADMIN_EMAIL` / `ADMIN_PASSWORD` from `.env`)

Shop details such as support email, phone and address live in
`frontend/src/app/core/config/site.config.ts`. Details: [frontend/README.md](frontend/README.md).

## Database tools

MySQL Workbench 26 is installed for browsing the database. Connect to `127.0.0.1:3306`
as `tshirt_app` (password in `.env` as `DB_PASSWORD`), schema `tshirt_shop`.

## Razorpay test setup

1. Sign in to the Razorpay Dashboard and switch to **Test Mode**.
2. Account & Settings, API Keys: generate a test key. Copy the Key ID (`rzp_test_...`) and Key Secret.
3. Put them in the repository `.env` (never commit it, never paste the secret anywhere else):

   ```
   RAZORPAY_KEY_ID=rzp_test_...
   RAZORPAY_KEY_SECRET=...
   RAZORPAY_WEBHOOK_SECRET=...   # used from Phase 14
   ```

4. Restart the backend. "Pay online" becomes available at checkout.
5. Pay with Razorpay test methods, for example UPI ID `success@razorpay` (succeeds) or `failure@razorpay` (fails), or the test cards listed in the Razorpay docs. Test payments never move real money.

**Webhooks.** In the Razorpay Dashboard, Account & Settings, Webhooks, add
`https://<your-domain>/api/payments/webhook`. Use the value of `RAZORPAY_WEBHOOK_SECRET` from `.env` as the
secret, and select `payment.authorized`, `payment.captured`, `payment.failed`, `order.paid`,
`refund.processed` and `refund.failed`. Razorpay cannot reach a local PC; locally, the reconciliation job
confirms payments within 5 minutes (or use a tunnel such as ngrok to test webhooks).

The Key Secret and Webhook Secret stay on the backend and are never sent to Angular.

Where the money goes: Razorpay settles payments to the bank account verified in your Razorpay
account (Account & Settings, Bank account details). It is not set in this code. Live mode needs
completed KYC and live keys (`rzp_live_...`).

## Environment variables

See [.env.example](.env.example) for the full list: database, JWT, Razorpay,
storage, mail and app settings. Never commit `.env`.

## API documentation

Swagger UI is generated by springdoc-openapi at `/swagger-ui.html` in the dev profile
and is disabled in production. The raw OpenAPI document is at `/v3/api-docs`.
The planned API surface is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Deployment

Covered in Phase 19: production builds, Nginx with HTTPS, environment
configuration, backups, logging and monitoring.

## Development workflow

See [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md).
