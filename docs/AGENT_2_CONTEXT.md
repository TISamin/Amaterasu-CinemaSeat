# Agent 2 — CinemaSeat Context

> **Purpose:** Persistent knowledge base for Agent 2 (Payment / Gateway / Reliability).
> A future Opus chat must read this file before doing any Agent 2 work.
>
> **Status at creation:** Repository contains only `docs/` (system design docs). No source code, no Compose, no gateway binary is present in the working tree.
> All gaps are marked `UNKNOWN — REQUIRES MANAGER REVIEW`.

---

## 1. Agent 2 Responsibilities (Authoritative Scope)

Agent 2 owns:

- Payment module (backend payment entity, service, controller)
- Gateway client (HTTP client to the provided payment gateway)
- Payment state machine implementation (PENDING → SUCCEEDED / FAILED / REFUNDED)
- Payment callback handler (POST `/api/payments/callback`)
- Payment idempotency (both at `/charge` via `Idempotency-Key`, and at callback via `event_id`)
- OTP integration (POST `/api/otp/send`, POST `/api/otp/verify`)
- Gateway failure handling
- Timeout handling
- Race-condition handling (callback may arrive before `/pay` returns)
- Duplicate callback handling
- Payment-related automated tests

**Single sentence mission:** *Make the unreliable gateway safe for the booking system.*

---

## 2. Ownership Boundaries (Do NOT Touch)

Agent 2 must **not** independently redesign, rename, or restructure:

- `ShowSeat` concept (Agent 1)
- Seat concurrency control (Agent 1) — atomic UPDATE, hold acquisition
- `Booking` entity / state (Agent 1)
- Frontend (Agent 3)
- `docker-compose.yml` (read-only for Agent 2)
- CI configuration

If Agent 2 needs something from Agent 1, Agent 2 must request a **clean service interface** with: required method, inputs, outputs, reason. Agent 2 must **not** modify Agent 1's modules.

---

## 3. Project Architecture Relevant to Payment

**As of repo inspection (locked-in stack):**

- **Language:** TypeScript on Node.js 20+ (Node 24.15 confirmed locally)
- **HTTP framework:** Fastify 4.x — fast, schema-first, low ceremony, plays well with raw-body capture (HMAC)
- **Database driver:** `pg` (node-postgres) — direct SQL with parameterized queries, no ORM. Required so DB UNIQUE constraints are the source of truth (no implicit per-entity abstractions).
- **Migrations:** `node-pg-migrate` — plain SQL migrations, versioned. Plays well with raw `pg`.
- **Tests:** Vitest — runs TS, supports both unit and integration tests.
- **Folder layout:**
  ```
  src/
    config/        env loading + validation
    db/            pg pool, migration runner
    payments/      domain types, repository, service, state machine
    gateway/       HTTP client (charge/refund/otp), HMAC helper
    ports/         BookingPaymentPort, ShowSeatPaymentPort interfaces + no-op adapters
    http/          Fastify routes, raw-body capture middleware
    app.ts         Fastify build (no listen)
    server.ts      listen + boot
  migrations/      node-pg-migrate SQL files
  tests/           vitest specs
  ```
- **API port:** `API_PORT` env var (default `3000`); Compose backend service name `api`
- **Authentication mechanism:** "intentionally simple for the hackathon" — `userId` is passed on hold; Agent 2 does not own auth. Token-less; identifiers come from URL or body.

### Reference architecture inferred from docs

```
Client (Agent 3)
   │
   ▼
Backend (Agent 1 owns /bookings, /seats; Agent 2 owns /payments, /otp)
   │
   ├── PostgreSQL (source of truth for seat, hold, booking, payment, payment_events)
   │
   └── HTTP ──► Provided Gateway (Docker service `gateway`, port 9000)
                  │
                  └─► async callback ──► POST /api/payments/callback (backend)
```

---

## 4. API Contracts (Agent 2 Surface)

From `docs/API_CONTRACT.md`. These are **non-negotiable** for Agent 2.

### 4.1 `POST /api/bookings/{bookingRef}/pay`

- Returns `202 Accepted` quickly with `{ bookingRef, paymentId, status: "PENDING" }`
- **Must NOT** wait for final payment success
- **Must NOT** hold an open DB transaction while waiting on the gateway
- Internally: validate booking → create/identify pending `payments` row → call gateway `/charge` → receive PENDING → return

### 4.2 `POST /api/payments/callback`

- Body: `{ event_id, payment_id, booking_ref, status, amount, currency, timestamp }`
- `status` ∈ `{SUCCEEDED, FAILED, REFUNDED}`
- **Duplicate callback (same `event_id`):** return `200 OK`, do nothing
- **Never** return non-2xx for a recognized duplicate (gateway would retry)

### 4.3 `POST /api/otp/send`

- Body: `{ phone, ref }`
- Forwards to gateway; gateway may silently drop ~10% of OTPs

### 4.4 `POST /api/otp/verify`

- Body: `{ ref, code }` → `{ verified: boolean }`
- Deterministic mode code: `123456`

### 4.5 `GET /health`

- Must return 200 quickly
- **Must NOT** depend on gateway availability (Agent 2 must not add a synchronous gateway ping here)

### 4.6 Status code conventions (must follow)

| Code | Use |
|------|-----|
| 200  | Successful read / operation, and **duplicate callback** |
| 201  | Explicit resource creation |
| 202  | Async payment initiation (`/pay`) |
| 400  | Invalid input |
| 404  | Missing resource |
| 409  | Seat / state conflict |
| 500  | Unexpected server error only |

---

## 5. Database / Payment Schema (Agent 2 Tables)

From `docs/DATABASE_CONTRACT.md` §14 and §15. These two tables are Agent 2's authoritative schema. Coordinate with Agent 1 on migration file naming.

### 5.1 `payments`

| Field         | Type / constraint                               |
|---------------|-------------------------------------------------|
| `id`          | PRIMARY KEY                                     |
| `payment_id`  | UNIQUE — gateway's `payment_id`                 |
| `booking_ref` | FK / reference to `bookings.booking_ref`        |
| `status`      | `PENDING` \| `SUCCEEDED` \| `FAILED` \| `REFUNDED` |
| `amount`      | numeric                                         |
| `currency`    | text (e.g. `"BDT"`)                             |
| `created_at`  | timestamp                                       |
| `updated_at`  | timestamp                                       |

Required indexes: `payments.payment_id`, `payments.booking_ref`.

### 5.2 `payment_events`

| Field         | Type / constraint                          |
|---------------|--------------------------------------------|
| `id`          | PRIMARY KEY                                |
| `event_id`    | **UNIQUE** — callback deduplication key    |
| `payment_id`  | text                                       |
| `booking_ref` | text                                       |
| `status`      | text (`SUCCEEDED`/`FAILED`/`REFUNDED`)     |
| `amount`      | numeric                                    |
| `currency`    | text                                       |
| `received_at` | timestamp                                  |

Required index: `payment_events.event_id`.

### 5.3 Callback idempotency mechanism (authoritative)

The `event_id` UNIQUE constraint is the deduplication primitive. Pattern:

```
1. INSERT into payment_events (event_id=...)   -- may throw unique-violation
2. if inserted:
       process payment state transition
   else:
       duplicate → return 200, no state change
```

The unique constraint also makes concurrent duplicate callbacks safe.

---

## 6. Payment State Machine

From `docs/STATE_MACHINE.md` §11–§13.

```
PENDING ──callback SUCCEEDED──► SUCCEEDED     (Booking→CONFIRMED,  ShowSeat→BOOKED)
PENDING ──callback FAILED─────► FAILED        (Booking→PAYMENT_FAILED)
SUCCEEDED ──refund────────────► REFUNDED
```

Forbidden:

- `CONFIRMED → PENDING_PAYMENT`
- `PAYMENT_FAILED → CONFIRMED` (without explicit new attempt)
- `BOOKED → HELD` or `BOOKED → AVAILABLE` (MVP)
- Duplicate callback → second state transition

Seat-release policy on `FAILED`: per `STATE_MACHINE.md` §4, a failed payment must not leave a permanently blocked seat; release the HELD ShowSeat back to AVAILABLE via Agent 1's agreed booking/seat service interface (Agent 2 must not modify `show_seats` directly).

---

## 7. Gateway Configuration

### 7.1 Network (non-negotiable)

- Gateway Docker service name: **`gateway`**
- Gateway port: **`9000`**
- URL the backend uses to reach the gateway: **`http://gateway:9000`**
- **Never** hardcode `localhost` for backend → gateway calls.

### 7.2 Callback URL (critical)

The `callback_url` sent to the gateway must be reachable **from inside the Docker network**. Use the backend Compose service name, e.g.:

```
http://api:<API_PORT>/api/payments/callback
```

**Do not** use `http://localhost:3000/webhooks/payment` or any `localhost` URL — the gateway container cannot reach it.

The exact service name and port come from `docker-compose.yml` (Agent 1 / team).

### 7.3 Idempotency key

- Use HTTP header **`Idempotency-Key`** when calling `/charge`.
- The key must be **stable per payment attempt** — for example, derived from `booking_ref` (e.g. `bk:<booking_ref>` or a UUID stored against the `payments` row at creation time).
- If `/charge` must be retried, the **same key must be reused** so the gateway does not create an unintended second payment.

### 7.4 Environment variables (assumed; confirm against Compose)

| Variable            | Purpose                                                |
|---------------------|--------------------------------------------------------|
| `GATEWAY_BASE_URL`  | `http://gateway:9000`                                  |
| `GATEWAY_SECRET`    | HMAC secret for `X-Signature` verification (bonus)     |
| `GATEWAY_CALLBACK_URL` | Full callback URL the gateway should hit            |
| `HOLD_TTL_SECONDS`  | Hold duration (Agent 1's responsibility; Agent 2 reads it for context only) |
| `GATEWAY_TIMEOUT_MS` | Suggested: HTTP timeout for `/charge` to avoid hangs |
| `OTP_DETERMINISTIC` | When true, expect `123456` (debug only)               |

---

## 8. Gateway Rules (Authoritative)

Agent 2 must implement and test these `X-Mock-Force` modes against the provided gateway:

| Header value     | What the gateway does                                        | Agent 2 must verify                                  |
|------------------|--------------------------------------------------------------|------------------------------------------------------|
| `success`        | Normal SUCCEEDED callback                                    | Booking→CONFIRMED, ShowSeat→BOOKED                    |
| `fail`           | FAILED callback                                              | Booking→PAYMENT_FAILED, seat released                |
| `duplicate`      | Same callback sent twice with same `event_id`                | Second is a no-op, both return 200                    |
| `timeout`        | `/charge` hangs / eventually fails                           | No open DB tx, no crash, no duplicate payment on retry |
| `race`           | Callback arrives before `/charge` response                   | System ends in correct final state                    |

(plus normal traffic without the header.)

### HMAC signature (bonus)

If implemented:

- Header: `X-Signature`
- Algorithm: **HMAC-SHA256**, key = `GATEWAY_SECRET`
- Sign the **raw request body bytes** (do not re-serialize parsed JSON before computing HMAC)
- Constant-time compare
- **Do not sacrifice core callback correctness to implement this.**

---

## 9. Callback Rules

On every callback delivery:

1. Read `event_id`.
2. Attempt to `INSERT` a `payment_events` row keyed by `event_id`.
3. If insert succeeds → first delivery: process state transitions.
4. If insert fails on `UNIQUE(event_id)` → duplicate: respond `200 OK`, do nothing.
5. **Never** respond non-2xx for a recognized duplicate — gateway interprets that as failure and retries forever.

State transitions on first delivery (atomic):

- `SUCCEEDED` → `payments.status = SUCCEEDED`, `bookings.status = CONFIRMED`, `show_seats.status = BOOKED` (via Agent 1's booking/seat interface).
- `FAILED` → `payments.status = FAILED`, `bookings.status = PAYMENT_FAILED`, release the HELD ShowSeat (via Agent 1's interface, never by Agent 2 mutating `show_seats` directly).
- `REFUNDED` → `payments.status = REFUNDED`. Refund effect on booking/seat only if team has explicitly agreed a refund policy — **default: no seat/booking change** in MVP.

---

## 10. Idempotency Rules

Two independent idempotency layers:

1. **Outbound `/charge`:** `Idempotency-Key` header, stable per payment attempt. Retries reuse the same key.
2. **Inbound `/api/payments/callback`:** `event_id` UNIQUE in `payment_events`. Duplicates short-circuit to HTTP 200.

Both must be exercised by automated tests.

---

## 11. Race-Condition Requirements

The gateway can fire the callback **before** the `/pay` HTTP call has returned. The system must remain correct.

Test mode: `X-Mock-Force: race`.

Robustness strategies (Agent 2 must combine, not pick one):

- Callback handler must look up the `payments` row by `payment_id` and/or `booking_ref`. If the row is missing (race: callback arrived first), create a minimal `payments` row keyed by the gateway's `payment_id` **before** processing the state transition. This requires `payments.payment_id` UNIQUE to remain safe.
- Booking transitions must be idempotent: `CONFIRMED → CONFIRMED` is a no-op (no second seat mutation).
- The `/pay` response handler, when it finally returns, must not assume the payment is still PENDING. If the callback already finalized it, `/pay` should read the current state and respond accordingly (still `202` to the client, body reflects current truth).

---

## 12. Timeout Requirements

`X-Mock-Force: timeout` causes `/charge` to hang. Agent 2 must ensure:

- **No open DB transaction** while waiting on `/charge`. Transaction pattern around `/pay`: open tx → read booking → insert pending `payments` row → COMMIT → close tx → call gateway → receive PENDING → respond 202. The gateway call happens **outside** any DB transaction.
- HTTP client uses an explicit timeout (`GATEWAY_TIMEOUT_MS`). On timeout, surface as a clean error; do not crash.
- On retry, the same `Idempotency-Key` is sent, so the gateway returns the same `payment_id` instead of creating a new one. No duplicate payments.

---

## 13. OTP Requirements

- `POST /api/otp/send`: forward to gateway; gateway may silently drop ~10%. Backend must not crash on gateway failure; return a clean 5xx or a structured error response.
- `POST /api/otp/verify`: forward to gateway. In deterministic mode, code is `123456`.
- OTP failures must not be allowed to crash the backend.

---

## 14. Testing Requirements

Agent 2 must write automated tests covering (at minimum):

- Successful payment (`success`)
- Failed payment (`fail`)
- Duplicate callback (same `event_id` twice)
- Gateway timeout (`timeout`)
- Race callback (`race`)
- Callback arriving before `/pay` persists the payment row
- Duplicate `event_id` under concurrent delivery (DB UNIQUE protects)
- Already-CONFIRMED booking receiving a second SUCCEEDED callback → no-op
- Gateway returning 5xx
- Refund handling (if implemented)
- OTP send / verify happy path + gateway-drop path

Tests must run against the real provided gateway in the agreed test harness, not a mock built by Agent 2.

---

## 15. Important Decisions and Constraints (Locked)

1. **Gateway is authoritative.** Do not build a mock gateway. Use the provided one.
2. **`/pay` is async only.** Never block on gateway final result; never hold a DB transaction across the gateway call.
3. **Duplicate callback = HTTP 200, always.** No 409.
4. **`event_id` is the callback dedup key.** `UNIQUE(event_id)` is non-negotiable.
5. **No direct `show_seats` mutation by Agent 2.** Always go through Agent 1's booking/seat service interface.
6. **No silent API contract changes.** Any deviation must be coordinated with the team.
7. **No modifications to `docker-compose.yml`, frontend, or CI by Agent 2.**

---

## 16. Open / Unknown Items

These are gaps the agent cannot fill from the repo alone. Resolved vs. still-open:

- Backend language / framework / build tool — **RESOLVED**: Node.js + TypeScript + Fastify (see §3)
- ORM / migration tool in use — **RESOLVED**: raw `pg` + `node-pg-migrate`
- Compose service name for the backend — **RESOLVED default**: `api`. Backend service name is configurable via `BACKEND_SERVICE_NAME` env (default `api`); callback URL is composed as `http://${BACKEND_SERVICE_NAME}:${API_PORT}/api/payments/callback` so Compose rename does not break code.
- Agent 1's exact booking/seat service interface — **RESOLVED via ports**: defined in `src/ports/booking-payment.port.ts` and `src/ports/show-seat-payment.port.ts`. Agent 1 implements these later; Agent 2 ships a `NoopBookingAdapter` + `NoopShowSeatAdapter` so the suite runs standalone.
- Refund scope in MVP — **RESOLVED default**: refund endpoint wired but default is no seat/booking change on REFUNDED (per `STATE_MACHINE.md` §13). Documented in code.
- Exact HMAC body bytes format for `X-Signature` (hex vs base64) — **ASSUMPTION**: hex-lowercase. Configurable via `SIGNATURE_ENCODING` env. Implemented as opt-in via `HMAC_ENABLED` (default false in dev, true in prod when `GATEWAY_SECRET` is set).
- Gateway base path layout (`/charge` vs `/v1/charge`) — **ASSUMPTION**: unversioned root. Configurable via `GATEWAY_PATH_PREFIX` env (default empty).
- `Idempotency-Key` format — **ASSUMPTION**: opaque string, max 255 chars. We use `pay:${booking_ref}:${attempt}` so retries are stable.
- OTP send response shape — **ASSUMPTION**: gateway returns `{ ok: true }` or `{ ok: false, error: '...' }`. Backend forwards the shape; if `ok: false`, returns 502 with structured error.
- Test environment — **ASSUMPTION**: tests run against an in-process fake HTTP server that emulates the gateway's `X-Mock-Force` modes. This is NOT a replacement for the real gateway; it's a thin test harness that obeys the documented contract. A second suite (`tests/integration.real-gateway.test.ts`) is wired but skipped unless `RUN_REAL_GATEWAY=1`.

---

## 17. Cross-References

- `docs/API_CONTRACT.md` — endpoints, request/response shapes, status codes
- `docs/DATABASE_CONTRACT.md` — table schemas, indexes, invariants, source-of-truth rules
- `docs/STATE_MACHINE.md` — allowed/forbidden transitions
- `docs/AGENT_2_PROGRESS.md` — live working state (read this first in any new chat)

---

## 18. Audit-Driven Fixes (H-1, H-2, H-3, H-7)

The manager audit identified four REQUIRED high-risk items. All four are fixed and verified in the current code base.

### H-1 — Callback amount/currency validation

**Threat:** A malicious or buggy gateway could send `amount` or `currency` that differs from the payment row. If trusted blindly, an attacker could pay less than the booking's price and the system would still mark the booking CONFIRMED.

**Fix (`src/payments/service.ts` `handleCallback`):**
- Look up the payment row inside the same transaction as the event insert.
- If the row exists and `row.amount !== callback.amount` OR `row.currency !== callback.currency`, set `amountMismatch: true`, **do NOT transition state**, record the event for audit, return HTTP 200 with `amountMismatch: true`.
- If the row does not exist (race: callback arrived before `/pay` persisted), look up `bookings.getAmountByRef` and use that to validate the callback's amount/currency. If the row is created late with mismatched values, treat as `amountMismatch`.

**Contract (locked):** amount/currency mismatch → record event, HTTP 200, `amountMismatch: true` in body, no state transition. Confirmed in `tests/payment-service.test.ts` (`PaymentService.handleCallback — amount validation (H-1)`) and `tests/integration/payment-dedup.integration.test.ts` (real PG).

### H-2 — `/pay` must never send `amount=0` to the gateway

**Threat:** A bug in amount resolution could charge zero, allowing free bookings.

**Fix (`src/payments/service.ts` `pay`):**
- The booking port is the **single source of truth** for `amount` and `currency` (`getAmountByRef`).
- If `price` is null, or `amount` is not finite / <= 0, or `currency` is empty, throw `PaymentValidationError` → HTTP 400 `PAYMENT_VALIDATION`.
- When creating a new pending payment row, use `price.amount` and `price.currency`. When reusing an existing pending row (retry), assert the row's amount/currency matches `price`; throw `PaymentValidationError` if they differ (booking changed underneath us).

**Contract (locked):** missing/invalid price → 400 `PAYMENT_VALIDATION`. `gateway.charge` is NEVER called with `amount <= 0`. Confirmed in `tests/payment-service.test.ts` (`PaymentService.pay — amount validation (H-2)`).

### H-3 — Real-PG integration test for duplicate callbacks

**Threat:** Unit tests use a mocked repository. A bug in the actual SQL (e.g. missing UNIQUE constraint) would not surface.

**Fix (`tests/integration/payment-dedup.integration.test.ts`):**
- Test connects to PostgreSQL via `INTEGRATION_DATABASE_URL` env var.
- Creates an isolated schema (`payment_dedup_<ts>_<rand>`), applies both migrations against it, runs the test, drops the schema in `afterAll`.
- Sends the SAME callback twice through the Fastify HTTP layer.
- Asserts: both HTTP 200; exactly one `payment_events` row; exactly one `payments.status` transition PENDING→SUCCEEDED; `confirmByRef` called exactly once.
- A second test asserts `amountMismatch` records the event but does NOT transition state.
- When `INTEGRATION_DATABASE_URL` is unset, the entire describe is skipped (no false failures in CI without a DB).

**Run locally:**
```powershell
$env:INTEGRATION_DATABASE_URL = "postgres://user:pass@host:5432/db"
npm test -- tests/integration/payment-dedup.integration.test.ts
```

### H-7 — Explicit refund policy

**Threat:** Ambiguity about whether a REFUNDED callback should reopen the seat or move the booking back to PENDING_PAYMENT could lead to inconsistent agent implementations.

**Fix (`src/payments/service.ts`):**
- Top-of-file JSDoc locks the MVP policy: "REFUNDED → booking stays CONFIRMED, seat stays BOOKED. No booking/seat port calls."
- `applyRefunded` JSDoc is explicit: it must NOT call `bookings.confirmByRef`, `bookings.markPaymentFailed`, or `seats.bookForBooking` / `seats.releaseForBooking`.
- The state-machine itself enforces this: `applyRefunded` is the only allowed SUCCEEDED→REFUNDED handler and it is a no-op against the ports.

**Contract (locked):** REFUNDED payment → no booking/seat side-effects. Confirmed in `tests/payment-service.test.ts` (`PaymentService — refund policy (H-7)`): after a SUCCEEDED callback clears the recorded calls, a REFUNDED callback adds zero new entries to either port's `calls` list.

### Items explicitly de-scoped by the manager

H-4, H-5, H-6, H-8, H-9 are deferred per manager instruction; current code preserves the existing contracts and does not introduce the deferred changes.