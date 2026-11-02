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

## Current endpoints (Phase 3)

| Method | Path | Access |
|--------|------|--------|
| GET | `/api/categories` | Public |
| GET | `/api/categories/{slug}` | Public |
| GET | `/api/reference/sizes?group=APPAREL\|WAIST` | Public |
| GET | `/api/reference/colors` | Public |
| GET | `/actuator/health`, `/actuator/info` | Public |
| any | `/api/admin/**` | `ROLE_ADMIN` (login arrives in Phase 4) |
| any | everything else | Authenticated |
