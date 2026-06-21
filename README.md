# Ecart Backend

Ecart is a Spring Boot application designed to manage cart operations, orders, checkout, and discount code management with SQLite.

## Prerequisites

- **Java**: JDK 21
- **Gradle**: Wrapper is provided in the repository

## Getting Started

### Running the Application

To run the application locally in the default dev mode (using SQLite database `data/ecart.db`):

```bash
./gradlew bootRun
```

The application will start on port `8080` by default.

### Running Tests

To run the full suite of unit and integration tests:

```bash
./gradlew clean test
```

## API Documentation

The project includes OpenAPI (Swagger UI) documentation:
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## Sample API Calls

Below are sample curl commands demonstrating core flows.

### 1. Add Item to Cart

Adds an item to the user's active cart. If the cart doesn't exist, a new active cart is created automatically.

```bash
curl -X POST http://localhost:8080/api/v1/cart/items \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123" \
  -d '{
    "sku": "ITEM-001",
    "name": "Premium Coffee Mug",
    "unitPrice": 1299,
    "quantity": 2
  }'
```

### 2. Get Active Cart

Retrieves the active cart for a specific user.

```bash
curl -X GET http://localhost:8080/api/v1/cart \
  -H "X-User-Id: user-123"
```

### 3. Checkout Cart

Checks out the active cart, placing an order. An `Idempotency-Key` header is required.

```bash
curl -X POST http://localhost:8080/api/v1/checkout \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123" \
  -H "Idempotency-Key: check-key-abc1" \
  -d '{
    "discountCode": "SAVE10-000003"
  }'
```

*(Note: `discountCode` is optional in the request body. Leave it out if no coupon is applied).*

### 4. Admin API: Generate Discount Code

Generates a discount code for the oldest eligible order that is a multiple of `nthOrder` and does not yet have a code generated.

```bash
curl -X POST http://localhost:8080/api/v1/admin/discount-codes \
  -H "Content-Type: application/json" \
  -H "X-Admin-Id: admin-99" \
  -H "X-Admin-Role: ADMIN" \
  -d '{
    "nthOrder": 3,
    "discountPercent": 10,
    "expiresInDays": 30
  }'
```

### 5. Admin API: View Summary Report

Returns report metrics including item counts, total revenue, generated codes, and total discounts applied.

```bash
curl -X GET http://localhost:8080/api/v1/admin/reports/summary \
  -H "X-Admin-Id: admin-99" \
  -H "X-Admin-Role: ADMIN"
```
