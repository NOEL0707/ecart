# Design Decisions

This document details the key architectural and design decisions made during the implementation of the Ecart backend, as required by the PRD.

---

## Decision 1: Database Access Technology

**Context:** The application needs a persistence layer for storing carts, orders, order items, and discount codes in SQLite.

**Options Considered:**
- **Option A: Spring Data JPA (Hibernate)**
  - *Pros:* Automatic query generation, ORM object mapping, rich ecosystem.
  - *Cons:* Heavy runtime dependency, potential schema generation mismatch with SQLite, complex transactional controls.
- **Option B: Spring JDBC with `JdbcTemplate`**
  - *Pros:* Keeps SQL statements explicit, direct mappings to database columns, lightweight, high performance, and precise transactional boundaries.

**Choice:** Option B: Spring JDBC with `JdbcTemplate`.

**Why:** Using `JdbcTemplate` keeps database interaction explicit. It avoids ORM overhead and issues with SQLite's lack of full JDBC spec coverage. This aligns with the TRD Section 2.1 requirement to keep SQL explicit and allow direct mapping to/from SQL tables.

---

## Decision 2: Every Nth Order Discount Generation Logic

**Context:** The TRD requires that every nth successful order is eligible to trigger exactly one discount code (e.g. order numbers 3, 6, 9, etc., when `nthOrder = 3`).

**Options Considered:**
- **Option A: Checking current order count (e.g., `totalCount % nthOrder == 0`)**
  - *Pros:* Simple.
  - *Cons:* Only works exactly at the moment the nth order is placed. If the count grows beyond that before the admin requests the generation (e.g. 4 orders placed), the check fails and eligible codes cannot be generated.
- **Option B: Database query matching ungenerated multiples of `n`**
  - *Pros:* Finds all eligible order numbers that do not yet have a code associated with them. Allows retroactive generation and idempotency.
  - *Cons:* Requires a slightly more complex SQL query.

**Choice:** Option B: Database query matching ungenerated multiples of `n` (`findOldestUngeneratedNthOrder`).

**Why:** Option B ensures the system remains robust. Even if the store processes more orders before the admin calls the discount code generation endpoint, the oldest eligible order number is selected, and a unique code is generated for it. This adheres to Section 5.4 of the TRD.

---

## Decision 3: Idempotency Validation for Cart Checkout

**Context:** The checkout process must be idempotent to prevent duplicate order generation from client retries.

**Options Considered:**
- **Option A: Blindly return the existing order if the idempotency key matches**
  - *Pros:* Simple to implement.
  - *Cons:* If the client modifies the cart or request body but sends the same key, it would return the old order, masking the request mismatch error.
- **Option B: Store request hash and validate key reuse**
  - *Pros:* Prevents double order placement while raising a conflict error (409) if the key is reused with a different request body.
  - *Cons:* Requires hashing request properties and persisting the hash.

**Choice:** Option B: Store request hash and validate key reuse.

**Why:** Option B is the industry standard for payment and checkout APIs. It guarantees that the same key only replays the exact same transaction, returning a conflict if a client uses the same key with different parameters.

---

## Decision 4: Date-Time Storage in SQLite

**Context:** SQLite does not have a native date-time storage class.

**Options Considered:**
- **Option A: Store as Unix epoch milliseconds (INTEGER)**
  - *Pros:* Simple comparison and sorting.
  - *Cons:* Not human-readable when querying the database directly.
- **Option B: Store as ISO-8601 formatted text (TEXT) in UTC**
  - *Pros:* Standard representation, lexicographically sortable, human-readable when browsing the database directly.
  - *Cons:* Requires formatting and parsing on the application side.

**Choice:** Option B: Store as ISO-8601 formatted text (TEXT) in UTC.

**Why:** Storing dates in UTC ISO-8601 format allows standard date-time representations that are easy to debug in raw SQLite databases while being fully compatible with standard Java `Instant` formatting and parsing.

---

## Decision 5: Admin Authorization Isolation

**Context:** The admin APIs need role-based access validation.

**Options Considered:**
- **Option A: Spring Security (full setup with OAuth2/JWT)**
  - *Pros:* Highly secure and standard.
  - *Cons:* Introduces massive configuration overhead and complexity for a simple mock authentication exercise.
- **Option B: Simple Interceptor or Service-Level Authorization**
  - *Pros:* Simple headers-based authorization logic isolated to a helper service class (`AdminAuthorizationService`).
  - *Cons:* Relies on headers passed directly from a gateway/load-balancer rather than cryptographic tokens.

**Choice:** Option B: Simple Service-Level Authorization.

**Why:** Option B keeps the codebase focused and simple for the current scope. It isolates the role checks in a dedicated service, meaning it can easily be replaced by a Spring Security filter later without modifying any controller endpoints or business logic contracts.
