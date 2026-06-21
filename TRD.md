# Technical Requirements Document: Ecart Backend

## 1. Purpose

This document is the technical source of truth for the Ecart backend implementation. It translates the product requirements in `PRD.md` into concrete technology, API, persistence, validation, observability, testing, and production-readiness requirements.

The backend must support:

- Adding items to a user's cart.
- Checking out a cart and placing an order.
- Validating and applying discount codes during checkout.
- Generating discount codes for every nth successful order.
- Reporting purchased item counts, revenue, discount codes, and total discounts given.

## 2. Technology Stack

### 2.1 Runtime and Language

- Java: 21.
- Build tool: Gradle with Gradle Wrapper.
- Framework: Spring Boot.
- API style: REST over HTTP with JSON request and response bodies.
- Primary database: SQLite.
- Database access: Spring JDBC with `JdbcTemplate`. This keeps SQL explicit, avoids unsupported SQLite repository dialect behavior, and gives the service layer precise transaction control.
- Schema management: versioned SQL migration files under `src/main/resources/db/migration`, executed by the application SQLite initializer. Flyway can replace the initializer when the database moves to a Flyway-supported production engine such as PostgreSQL.
- Validation: Jakarta Bean Validation.
- Serialization: Jackson.
- Logging: SLF4J with Logback JSON-friendly structured logs.
- API documentation: OpenAPI 3 via `springdoc-openapi`.
- Swagger UI: `/swagger-ui.html`.
- OpenAPI JSON: `/v3/api-docs`.

### 2.2 Testing Stack

- Unit tests: JUnit 5 and AssertJ.
- Mocking: Mockito only where collaboration boundaries require it.
- API/integration tests: Spring Boot test with MockMvc or RestAssured.
- BDD tests: Cucumber may be used for user-flow scenarios such as cart checkout and discount eligibility.
- Test database: isolated SQLite temporary database per test suite or test class.
- Testcontainers: optional and not required for SQLite. Use Testcontainers only if future external infrastructure is introduced, such as PostgreSQL, Kafka, Redis, or a dependent HTTP service.
- Coverage expectation: business rules for discount eligibility, checkout totals, and coupon validation must have focused unit tests.

### 2.3 Recommended Dependencies

Required:

- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-actuator`
- `org.springframework.boot:spring-boot-starter-jdbc`
- `org.xerial:sqlite-jdbc`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`

Test:

- `org.springframework.boot:spring-boot-starter-test`
- `org.assertj:assertj-core`
- `io.rest-assured:rest-assured` or Spring `MockMvc`
- `io.cucumber:cucumber-java` and `io.cucumber:cucumber-junit-platform-engine` if BDD scenarios are implemented.

## 3. Architecture

### 3.1 Logical Layers

The application must be organized into explicit layers:

- Controller layer: HTTP request/response mapping, header extraction, input validation, response status mapping.
- Application service layer: use-case orchestration and transaction boundaries.
- Domain layer: cart, order, discount, and reporting rules.
- Repository layer: SQLite persistence access.
- Infrastructure layer: request correlation, logging, exception handling, configuration, migrations.

Controllers must not contain business rules beyond request validation and response shaping.

### 3.2 Package Structure

Recommended package layout:

```text
ecart.com
  config
  controller
  controller.dto
  domain
  exception
  repository
  service
  observability
```

## 4. Production-Ready Request Controls

### 4.1 Required Request Headers

Every non-actuator API request must support the following headers:

| Header | Required | Description |
| --- | --- | --- |
| `X-Correlation-Id` | Optional from client, required internally | Request correlation id. If missing, the server generates one. Must be returned in every response. |
| `X-User-Id` | Required for user APIs | Stable user identifier for cart and checkout operations. |
| `X-Request-Source` | Optional | Calling client name, such as `web`, `mobile`, `postman`, or `admin-tool`. |
| `Idempotency-Key` | Required for checkout | Client-provided unique key to prevent duplicate order creation for retried checkout requests. |
| `Content-Type` | Required for request bodies | Must be `application/json`. |
| `Accept` | Optional | Defaults to `application/json`; unsupported response types return `406`. |

Admin APIs must additionally require:

| Header | Required | Description |
| --- | --- | --- |
| `X-Admin-Id` | Required for admin APIs | Stable admin/operator identifier. |
| `X-Admin-Role` | Required for admin APIs | Must contain an authorized role such as `ADMIN`. |

Authentication can be simplified for the assignment, but the code must isolate admin authorization in a filter/interceptor or service so it can later be replaced by JWT/OAuth2 without changing controller contracts.

### 4.2 Response Headers

Every API response must include:

| Header | Description |
| --- | --- |
| `X-Correlation-Id` | Correlation id used for the request. |
| `Cache-Control` | `no-store` for mutable user/admin API responses. |
| `Content-Type` | `application/json`. |

Checkout responses should also include:

| Header | Description |
| --- | --- |
| `Idempotency-Key` | Echo of the checkout idempotency key when provided. |

### 4.3 Error Response Contract

All errors must use a consistent JSON shape:

```json
{
  "timestamp": "2026-06-20T10:15:30Z",
  "status": 400,
  "errorCode": "INVALID_DISCOUNT_CODE",
  "message": "Discount code is invalid or already used.",
  "correlationId": "9f1f8a64-4457-4f9f-b0d5-6f44f86de01f",
  "path": "/api/v1/checkout",
  "details": [
    {
      "field": "discountCode",
      "reason": "must reference an active unused code"
    }
  ]
}
```

Error handling must be centralized with `@ControllerAdvice`.

## 5. API Design

All endpoints are versioned under `/api/v1`.

### 5.1 Add Item to Cart

```http
POST /api/v1/cart/items
```

Required headers:

- `X-Correlation-Id`: optional, generated if absent.
- `X-User-Id`: required.
- `Content-Type: application/json`.

Request:

```json
{
  "sku": "ITEM-001",
  "name": "Coffee Mug",
  "unitPrice": 1299,
  "quantity": 2
}
```

Validation:

- `sku` is required, trimmed, max 64 characters.
- `name` is required, trimmed, max 255 characters.
- `unitPrice` is required and must be greater than or equal to 0.
- `quantity` is required and must be greater than 0.
- Monetary values are stored in minor units, for example cents or paise, as integer values.

Response: `201 Created`

```json
{
  "cartId": "cart_01HZX7JH3B6QQEW9BS9JXJ8Q6A",
  "userId": "user-123",
  "items": [
    {
      "sku": "ITEM-001",
      "name": "Coffee Mug",
      "unitPrice": 1299,
      "quantity": 2,
      "lineTotal": 2598
    }
  ],
  "subtotal": 2598
}
```

Business behavior:

- The active cart is scoped by `X-User-Id`.
- Adding the same SKU to an active cart increments quantity instead of creating duplicate line items.
- Checked-out carts are immutable.

### 5.2 Get Active Cart

```http
GET /api/v1/cart
```

Required headers:

- `X-User-Id`: required.

Response: `200 OK`

```json
{
  "cartId": "cart_01HZX7JH3B6QQEW9BS9JXJ8Q6A",
  "userId": "user-123",
  "items": [],
  "subtotal": 0
}
```

Business behavior:

- If no active cart exists, return an empty active cart response.

### 5.3 Checkout

```http
POST /api/v1/checkout
```

Required headers:

- `X-User-Id`: required.
- `Idempotency-Key`: required.
- `Content-Type: application/json`.

Request:

```json
{
  "discountCode": "SAVE10"
}
```

`discountCode` is optional. If supplied, it must be active, unused, and not expired.

Response: `201 Created`

```json
{
  "orderId": "ord_01HZX7S16M2XWS2R6SQTA4YFQP",
  "userId": "user-123",
  "items": [
    {
      "sku": "ITEM-001",
      "name": "Coffee Mug",
      "unitPrice": 1299,
      "quantity": 2,
      "lineTotal": 2598
    }
  ],
  "subtotal": 2598,
  "discountCode": "SAVE10",
  "discountPercent": 10,
  "discountAmount": 260,
  "total": 2338,
  "status": "PLACED",
  "createdAt": "2026-06-20T10:15:30Z"
}
```

Business behavior:

- Checkout fails if the active cart is empty.
- Checkout must be transactional.
- On successful checkout, the active cart becomes checked out and immutable.
- If a valid discount code is used, it is marked used in the same transaction as order creation.
- The same `Idempotency-Key` for the same user must return the original order response instead of creating a second order.
- Reusing an `Idempotency-Key` with a different request body must return `409 Conflict`.
- Discount amount calculation must be deterministic: `round half up` to nearest minor unit.

### 5.4 Generate Discount Code

```http
POST /api/v1/admin/discount-codes
```

Required headers:

- `X-Admin-Id`: required.
- `X-Admin-Role`: required and authorized.
- `Content-Type: application/json`.

Request:

```json
{
  "nthOrder": 3,
  "discountPercent": 10,
  "expiresInDays": 30
}
```

Validation:

- `nthOrder` must be greater than 0.
- `discountPercent` must be between 1 and 100.
- `expiresInDays` must be greater than 0 when supplied.

Response when eligible order threshold is satisfied: `201 Created`

```json
{
  "code": "SAVE10-000003",
  "discountPercent": 10,
  "triggeredByOrderNumber": 3,
  "status": "ACTIVE",
  "expiresAt": "2026-07-20T10:15:30Z"
}
```

Response when threshold is not currently satisfied: `409 Conflict`

```json
{
  "timestamp": "2026-06-20T10:15:30Z",
  "status": 409,
  "errorCode": "DISCOUNT_NOT_ELIGIBLE",
  "message": "No undiscounted nth order is currently eligible for discount code generation.",
  "correlationId": "9f1f8a64-4457-4f9f-b0d5-6f44f86de01f",
  "path": "/api/v1/admin/discount-codes",
  "details": []
}
```

Business behavior:

- Every nth successful order is eligible to trigger exactly one discount code. For example, when `nthOrder = 3`, order numbers `3`, `6`, `9`, and so on are eligible.
- The generated code must be unique.
- Admin generation must create a code for the oldest eligible order number that does not already have a generated code.
- Admin generation must be idempotent per eligible order number. Repeated calls must not create multiple codes for the same triggering order.
- Generated codes are single-use.
- Codes apply only to future checkout requests, not to the order that generated the code.

### 5.5 Admin Summary Report

```http
GET /api/v1/admin/reports/summary
```

Required headers:

- `X-Admin-Id`: required.
- `X-Admin-Role`: required and authorized.

Optional query parameters:

- `from`: ISO-8601 date-time inclusive.
- `to`: ISO-8601 date-time exclusive.

Response: `200 OK`

```json
{
  "itemsPurchasedCount": 25,
  "revenue": 125000,
  "discountCodes": [
    {
      "code": "SAVE10-000003",
      "discountPercent": 10,
      "status": "USED",
      "triggeredByOrderNumber": 3,
      "usedByOrderId": "ord_01HZX7S16M2XWS2R6SQTA4YFQP"
    }
  ],
  "totalDiscountGiven": 4500,
  "ordersCount": 10
}
```

Business behavior:

- Revenue is the sum of final order totals after discounts.
- `totalDiscountGiven` is the sum of discount amounts actually applied to orders.
- `itemsPurchasedCount` is the sum of purchased quantities across placed orders.

## 6. SQLite Data Model

### 6.1 Tables

#### `carts`

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | TEXT | Primary key |
| `user_id` | TEXT | Not null |
| `status` | TEXT | Not null, one of `ACTIVE`, `CHECKED_OUT` |
| `created_at` | TEXT | Not null ISO-8601 UTC |
| `updated_at` | TEXT | Not null ISO-8601 UTC |

Indexes:

- Unique partial index for one active cart per user: `(user_id)` where `status = 'ACTIVE'`.

#### `cart_items`

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | TEXT | Primary key |
| `cart_id` | TEXT | Not null, references `carts(id)` |
| `sku` | TEXT | Not null |
| `name` | TEXT | Not null |
| `unit_price` | INTEGER | Not null |
| `quantity` | INTEGER | Not null |
| `created_at` | TEXT | Not null ISO-8601 UTC |
| `updated_at` | TEXT | Not null ISO-8601 UTC |

Indexes:

- Unique index on `(cart_id, sku)`.

#### `orders`

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | TEXT | Primary key |
| `order_number` | INTEGER | Not null unique, monotonically increasing |
| `user_id` | TEXT | Not null |
| `cart_id` | TEXT | Not null, references `carts(id)` |
| `subtotal` | INTEGER | Not null |
| `discount_code` | TEXT | Nullable |
| `discount_percent` | INTEGER | Nullable |
| `discount_amount` | INTEGER | Not null default 0 |
| `total` | INTEGER | Not null |
| `status` | TEXT | Not null, one of `PLACED` |
| `idempotency_key` | TEXT | Not null |
| `request_hash` | TEXT | Not null |
| `created_at` | TEXT | Not null ISO-8601 UTC |

Indexes:

- Unique index on `(user_id, idempotency_key)`.
- Unique index on `order_number`.

#### `order_items`

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | TEXT | Primary key |
| `order_id` | TEXT | Not null, references `orders(id)` |
| `sku` | TEXT | Not null |
| `name` | TEXT | Not null |
| `unit_price` | INTEGER | Not null |
| `quantity` | INTEGER | Not null |
| `line_total` | INTEGER | Not null |

Indexes:

- Index on `order_id`.

#### `discount_codes`

| Column | Type | Constraint |
| --- | --- | --- |
| `code` | TEXT | Primary key |
| `discount_percent` | INTEGER | Not null |
| `status` | TEXT | Not null, one of `ACTIVE`, `USED`, `EXPIRED` |
| `triggered_by_order_number` | INTEGER | Not null unique |
| `used_by_order_id` | TEXT | Nullable, references `orders(id)` |
| `created_at` | TEXT | Not null ISO-8601 UTC |
| `expires_at` | TEXT | Nullable ISO-8601 UTC |
| `used_at` | TEXT | Nullable ISO-8601 UTC |

Indexes:

- Unique index on `triggered_by_order_number`.
- Index on `(status, expires_at)`.

### 6.2 SQLite Requirements

- Enable foreign keys with `PRAGMA foreign_keys = ON`.
- Use WAL mode where supported with `PRAGMA journal_mode = WAL`.
- Use integer minor units for all monetary values.
- Store date-time values in UTC ISO-8601 text format.
- Migrations must be repeatable from an empty database.
- No business data should be seeded in production profile.

## 7. Core Business Rules

### 7.1 Cart Rules

- Each user may have only one active cart.
- Cart item quantity must be positive.
- Cart item unit price cannot be negative.
- Active cart totals are computed from current cart items.
- Checked-out carts cannot be modified.

### 7.2 Checkout Rules

- Checkout requires an active non-empty cart.
- Checkout creates an immutable order snapshot.
- Checkout marks the cart as `CHECKED_OUT`.
- Checkout must be idempotent by `(userId, idempotencyKey)`.
- Order number assignment must be serialized to avoid duplicate nth-order calculations.

### 7.3 Discount Rules

- Every nth placed order is eligible to trigger one discount code.
- Discount code generation is an admin action.
- A discount code can be used once.
- A discount code must be active and unexpired at checkout time.
- Discount codes cannot reduce total below 0.
- Discount codes are consumed atomically with order creation.

## 8. Non-Functional Requirements

### 8.1 Observability

- Every log line produced during a request must include `correlationId`.
- User-facing APIs should log `userId`; admin APIs should log `adminId`.
- Do not log full request bodies when they contain sensitive data.
- Log checkout completion with order id, subtotal, discount amount, and total.
- Expose Spring Actuator health endpoint at `/actuator/health`.

### 8.2 Security

- Validate all request bodies.
- Reject unknown or malformed JSON with `400 Bad Request`.
- Enforce admin API headers through a centralized interceptor/filter.
- Return `403 Forbidden` for missing or unauthorized admin role.
- Do not expose stack traces in API responses.
- Use `Cache-Control: no-store` for mutable API responses.

### 8.3 Reliability

- Checkout and discount consumption must be handled in a single transaction.
- Idempotency must protect clients from network retry duplicate orders.
- SQLite write paths must keep transactions short.
- Failed checkout must leave cart and discount code state unchanged.

### 8.4 Performance

- Expected assignment-scale performance: single-node application with SQLite.
- Queries used by report APIs must rely on indexed columns.
- Avoid loading all orders into memory for reporting; aggregate in SQL.

## 9. Configuration

Application configuration must support profiles:

- `local`: local SQLite database file under application working directory.
- `test`: temporary SQLite database, isolated for tests.
- `prod`: externally configured SQLite database path.

Required properties:

```properties
spring.application.name=ecart
ecart.discount.default-nth-order=3
ecart.discount.default-percent=10
ecart.admin.allowed-roles=ADMIN
ecart.sqlite.path=./data/ecart.db
```

Secrets must not be committed to source control.

## 10. Controller and Service Responsibilities

### 10.1 Controllers

Controllers must:

- Map versioned routes.
- Validate request bodies.
- Extract required headers.
- Return documented HTTP status codes.
- Return DTOs, never persistence entities.
- Avoid business calculations.

### 10.2 Services

Services must:

- Own transaction boundaries.
- Enforce business rules.
- Compute totals and discounts.
- Coordinate cart, order, and discount repositories.
- Produce deterministic results for idempotent operations.

### 10.3 Repositories

Repositories must:

- Encapsulate SQL/database access.
- Never expose SQLite-specific details to controllers.
- Provide atomic operations for discount code consumption and checkout.

## 11. HTTP Status Code Requirements

| Scenario | Status |
| --- | --- |
| Successful cart item add | `201 Created` |
| Successful cart retrieval | `200 OK` |
| Successful checkout | `201 Created` |
| Successful idempotent checkout replay | `200 OK` |
| Successful discount code generation | `201 Created` |
| Successful admin report retrieval | `200 OK` |
| Validation failure | `400 Bad Request` |
| Missing required user/admin header | `400 Bad Request` |
| Unauthorized admin role | `403 Forbidden` |
| Empty cart checkout | `409 Conflict` |
| Invalid, used, or expired discount code | `409 Conflict` |
| Idempotency key reused with different body | `409 Conflict` |
| Unsupported media type | `415 Unsupported Media Type` |
| Unhandled server error | `500 Internal Server Error` |

## 12. Definition of Done

The implementation is complete when:

- All endpoints in this TRD are implemented.
- SQLite schema migration SQL exists and runs on startup through the database initializer.
- Required request and response headers are handled consistently.
- Global error response format is implemented.
- Unit tests cover cart totals, checkout, idempotency, discount validation, and nth-order discount generation.
- API/integration tests cover successful and failing checkout flows.
- Admin report aggregates are computed from persisted orders and discount records.
- `README.md` explains local run, tests, and sample API calls.
- `DECISIONS.md` records at least five design decisions as required by the PRD.

## 13. Explicit Out of Scope for Initial Version

- Frontend UI.
- Payment gateway integration.
- Inventory management.
- Real authentication provider integration.
- Multi-node horizontal scaling.
- Migration to PostgreSQL or another production database.

The design must keep these future additions possible without changing the external API contracts documented here.
