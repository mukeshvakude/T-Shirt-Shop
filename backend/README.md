# Backend (Spring Boot)

REST API for the T-Shirt Shop. Java 21, Spring Boot 4.1.2, MySQL 8 with Flyway.

## Versions

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 4.1.2 (Spring Framework 7, Spring Security 7, Hibernate 7) |
| Maven | 3.10.0 via the included wrapper (`mvnw`) |
| springdoc-openapi | 3.2.0 |
| MapStruct | 1.6.3 |
| MySQL Connector/J, Flyway | managed by Spring Boot |

## Package layout (`com.shop`)

| Package | Contents |
|---------|----------|
| `config` | `AppProperties` (`app.*` settings), CORS, OpenAPI, UTC `Clock`, `RequestIdFilter` |
| `controller` | REST controllers (thin, no business logic) |
| `service`, `service.impl` | Service interfaces and transactional implementations |
| `repository` | Spring Data JPA repositories |
| `entity` | JPA entities. `BaseEntity` holds id, `created_at`, `updated_at` |
| `dto` | Request/response records, `PageResponse` pagination envelope |
| `mapper` | MapStruct mappers (Spring components, unmapped fields fail the build) |
| `security` | `SecurityConfig`, JSON 401/403 handler. JWT arrives in Phase 4 |
| `exception` | `ApiException` hierarchy and `GlobalExceptionHandler` |
| `payment` | Razorpay integration (Phases 13 and 14) |
| `util` | `MoneyUtils` (INR and paise), `SlugUtils` |

## Configuration and profiles

Settings come from environment variables. For local runs, the git-ignored `.env`
in the repository root is imported automatically. See `../.env.example`.

| Profile | Use | Flyway locations | Swagger | Logs |
|---------|-----|------------------|---------|------|
| `dev` (default) | Local development | `db/migration` + `db/dev-data` (sample catalog) | On | Text, `com.shop` at DEBUG |
| `test` | Integration tests | `db/migration` on `tshirt_shop_test` | On | Text |
| `prod` | Production | `db/migration` only | Off | JSON (ECS) |

Hibernate runs with `ddl-auto=validate`. Flyway owns the schema.

## Run

```bash
cd backend
./mvnw spring-boot:run                 # dev profile, http://localhost:8080
```

On Windows use `mvnw.cmd`. `JAVA_HOME` must point to JDK 21.

| URL | Purpose |
|-----|---------|
| http://localhost:8080/swagger-ui.html | Swagger UI (dev only) |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |
| http://localhost:8080/actuator/health | Health, including database |

## Test

```bash
./mvnw test      # unit and web-slice tests, no database needed
./mvnw verify    # also runs *IT integration tests against MySQL tshirt_shop_test
```

## Build for production

```bash
./mvnw -DskipTests package
java -jar target/tshirt-shop-backend-*.jar --spring.profiles.active=prod
```

## Error format

Every error is an RFC 7807 problem detail with extra fields:

```json
{
  "type": "https://tshirt-shop.local/errors/resource-not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Category not found: ghost",
  "instance": "/api/categories/ghost",
  "code": "RESOURCE_NOT_FOUND",
  "timestamp": "2026-11-02T10:35:21.574Z",
  "requestId": "f626858d-b9ff-48a4-8094-073ec9c6a8f9"
}
```

Validation errors add an `errors` array of `{ "field", "message" }`. Each response carries
an `X-Request-Id` header that matches the `requestId` in logs.

## Current endpoints (Phase 14)

| Method | Path | Access |
|--------|------|--------|
| POST | `/api/payments/create-order` | Signed in (start or retry Razorpay payment for my unpaid online order; returns the public Key ID and Razorpay order) |
| POST | `/api/payments/verify` | Signed in (Razorpay signature checked with the Key Secret; confirms the order) |
| POST | `/api/payments/webhook` | Public, Razorpay only: `X-Razorpay-Signature` (HMAC-SHA256 of the raw body with the webhook secret), idempotent by `X-Razorpay-Event-Id` |
| POST | `/api/payments/failed` | Signed in (records a failed attempt reported by Checkout.js) |
| GET | `/api/checkout` | Signed in (validated cart, totals split, contact, saved addresses, payment options) |
| POST | `/api/checkout/place-order` | Signed in (creates the order; COD confirmed, online orders wait for payment with a stock hold) |
| GET | `/api/orders/{orderNumber}` | Signed in, own orders only |
| GET, DELETE | `/api/cart` | Signed in (cart with current prices, stock status, coupon, shipping, total; DELETE empties it) |
| POST | `/api/cart/items` | Signed in (`sku` for a product, or `designCode` + optional `sizeCode` for a custom T-Shirt) |
| PATCH, DELETE | `/api/cart/items/{id}` | Signed in (quantity, size, colour; remove) |
| POST | `/api/cart/merge` | Signed in (move the browser guest bag into the cart) |
| PUT, DELETE | `/api/cart/coupon` | Signed in (apply or remove a coupon code) |
| POST | `/api/cart/quote` | Public (price a guest bag of SKUs and quantities) |
| GET | `/api/pricing/charges` | Public (active extra charges, shipping rules, max quantity; not cached) |
| POST | `/api/pricing/design-estimate` | Public (price breakdown for custom T-Shirt choices and quantity, calculated on the server) |
| GET, PUT | `/api/admin/pricing/charges` | `ROLE_ADMIN` (all chargeable choices; PUT creates or updates one charge) |
| GET, PUT | `/api/admin/pricing/shipping` | `ROLE_ADMIN` (flat charge, free-shipping threshold) |
| GET | `/api/designer/options` | Public (T-Shirt types, colours, sizes, print areas, fonts, stock, upload limits; 60 s cache) |
| POST | `/api/designs/assets` | Signed in (multipart `file`, PNG/JPG checked by content, 40 per hour) |
| GET, POST | `/api/designs` | Signed in (my designs; save a new design) |
| GET, PUT, DELETE | `/api/designs/{code}` | Owner (or admin for GET); PUT and DELETE refused once the design is in an order |
| GET | `/api/admin/designs`, `/{code}`, `/{code}/download` (ZIP) | `ROLE_ADMIN` |
| GET | `/media/designs/{file}`, `/media/previews/{file}` | Unlisted random URLs, private cache, noindex |
| GET | `/api/account/dashboard` | Signed in (profile, order counts, recent orders, wishlist and address summary) |
| GET, PUT | `/api/account/profile` | Signed in (name, mobile) |
| PUT | `/api/account/email`, `/api/account/password` | Signed in, current password required; password change renews the session |
| GET, POST, PUT, DELETE | `/api/account/addresses[/{id}]`, PUT `/{id}/default` | Signed in, own addresses only |
| GET | `/api/account/states` | Signed in (states and union territories) |
| GET | `/api/products/facets?category&q` | Public (sizes, colours, price range, categories with counts) |
| GET | `/api/products/{slug}/reviews?page&size` | Public (approved reviews) |
| GET, PUT, DELETE | `/api/account/wishlist`, `/ids`, `/{productId}` | Signed in |
| GET | `/api/home` | Public (all home page sections, 60 s cache) |
| GET | `/api/offers`, `/api/reviews/highlights?limit=6` | Public |
| POST | `/api/newsletter`, `/api/contact` | Public, rate limited, always 202 |
| GET | `/api/products?q&category&minPrice&maxPrice&sizes&colors&featured&newArrival&inStock&sort&page&size` | Public |
| GET | `/api/products/{slug}`, `/api/products/{slug}/related` | Public |
| GET | `/media/products/{file}` | Public (uploaded images) |
| GET, POST, PUT, DELETE | `/api/admin/categories[/{id}]` | `ROLE_ADMIN` |
| GET, POST, PUT, DELETE | `/api/admin/products[/{id}]`, PATCH `/{id}/status` | `ROLE_ADMIN` |
| PUT | `/api/admin/products/{id}/variants` | `ROLE_ADMIN` |
| POST, PATCH, DELETE | `/api/admin/products/{id}/images[/{imageId}]` | `ROLE_ADMIN` |
| GET | `/api/admin/inventory/low-stock`, PATCH `/api/admin/inventory/{id}` | `ROLE_ADMIN` |
| POST | `/api/auth/register`, `/login`, `/refresh`, `/logout` | Public |
| POST | `/api/auth/forgot-password`, `/reset-password` | Public |
| GET | `/api/auth/me` | Logged in |
| GET | `/api/categories`, `/api/categories/{slug}` | Public |
| GET | `/api/reference/sizes?group=APPAREL\|WAIST`, `/api/reference/colors` | Public |
| GET | `/actuator/health`, `/actuator/info` | Public |
| any | `/api/admin/**` | `ROLE_ADMIN` |
| any | everything else | Logged in |

## Authentication

- Log in with `POST /api/auth/login`. Send the returned `accessToken` as `Authorization: Bearer <token>`.
- The refresh token is an HttpOnly cookie named `tshirt_rt`. Call `POST /api/auth/refresh` with credentials included to get a new access token before the 15 minutes run out.
- The first admin account comes from `ADMIN_EMAIL` and `ADMIN_PASSWORD` on first start.
- Until email delivery exists (Phase 17), the dev profile writes password reset links to the log. Search the log for `[DEV] Password reset link`.
- Full security design: [../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md#42-security-implemented-in-phase-4).

Required security variables:

| Variable | Notes |
|----------|-------|
| `JWT_SECRET` | Base64, at least 32 bytes decoded. Generate with `openssl rand -base64 64`. The app refuses to start otherwise |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | First admin. Password at least 12 characters with a letter and a digit |
| `REFRESH_COOKIE_SECURE` | Default `true`. Browsers accept Secure cookies on `http://localhost`, so keep it on |
