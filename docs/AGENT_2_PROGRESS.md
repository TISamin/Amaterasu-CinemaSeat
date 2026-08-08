# Agent 2 — CinemaSeat Working State

> **Purpose:** Live working-state file. Read this first in every new Opus chat.
> Updated after every meaningful change. Do **not** let it go stale.

---

## Current Branch

`agent-2-payment`.

---

## Repository Snapshot at Start

- Repo contained only `docs/`: `API_CONTRACT.md`, `DATABASE_CONTRACT.md`, `STATE_MACHINE.md`.
- **No source code, no `docker-compose.yml`, no backend framework, no gateway binary.**
- Single commit: `bfe5e15 docs: add system design documentation`.

---

## What Has Been Completed

### Phase 1 — Documentation
- [x] Inspected repo; confirmed scope of source code absence.
- [x] Read all three contract docs end-to-end.
- [x] Authored `docs/AGENT_2_CONTEXT.md`.
- [x] Authored `docs/AGENT_2_PROGRESS.md` (this file).

### Phase 2 — Implementation (manager: "PROCEED")
- [x] Stack chosen and committed to: **Node.js 24 + TypeScript 5.7 (strict) + Fastify 4 + raw `pg` + `node-pg-migrate` + Vitest + `undici`**.
- [x] Project scaffolding (`package.json`, `tsconfig.json`, `.gitignore`, `.env.example`, `vitest.config.ts`).
- [x] `npm install` (162 packages).
- [x] `src/config/env.ts` — typed env loading + `buildCallbackUrl(cfg)`.
- [x] `src/db/pool.ts` — `initPool`, `setPool` (test seam), `getPool`, `withTx`, `closePool`.
- [x] `migrations/1700000000000_payments.sql` — `payments` table with **UNIQUE(payment_id)** + **UNIQUE(idempotency_key)** + CHECK(status) + indexes.
- [x] `migrations/1700000000001_payment_events.sql` — `payment_events` table with **UNIQUE(event_id)** (dedup primitive).
- [x] `src/payments/types.ts` — domain types.
- [x] `src/payments/repository.ts` — race-safe `processCallbackInTx` catches PG `23505` for duplicate `event_id`.
- [x] `src/payments/state.ts` — forward-only state machine per `STATE_MACHINE.md`.
- [x] `src/payments/errors.ts` — `PaymentNotFoundError`, `PaymentTerminalError`, `PaymentGatewayTimeoutError`.
- [x] `src/ports/booking-payment.port.ts` — `BookingPaymentPort` interface + `NoopBookingAdapter`.
- [x] `src/ports/show-seat-payment.port.ts` — `ShowSeatPaymentPort` interface + `NoopShowSeatAdapter`.
- [x] `src/gateway/hmac.ts` — `signBody` + `verifySignature` over the **raw** body, using `timingSafeEqual`.
- [x] `src/gateway/client.ts` — `GatewayClient` (charge/refund/otpSend/otpVerify), `GatewayError`, `GatewayTimeoutError`, `makeGatewayClient(cfg)`. Sets `Idempotency-Key`, optional `X-Mock-Force`, configurable timeout.
- [x] `src/payments/service.ts` — `PaymentService` (single owner of payment state). Public methods: `pay`, `handleCallback`, `refund`, `otpSend`, `otpVerify`. Race-handling: callback may create a minimal PENDING row if `/pay` hasn't.
- [x] `src/http/routes.ts` — `/health`, `/api/bookings/:ref/pay`, `/api/payments/callback` (rawBody), `/api/payments/:id/refund`, `/api/otp/send`, `/api/otp/verify`. Error mappers: `mapPayError` (404/409/504/502), `mapGatewayError`. Duplicate callback → 200.
- [x] `src/app.ts` — Fastify builder with content-type parser that captures `rawBody` when the route opts in via `{ config: { rawBody: true } }`.
- [x] `src/server.ts` — boot: load config, init pool, build app, listen, graceful shutdown on SIGINT/SIGTERM.

### Phase 3 — Tests
- [x] `tests/hmac.test.ts` (4 tests) — sign/verify hex & base64; rejects tampered body & wrong secret.
- [x] `tests/state-machine.test.ts` (8 tests) — every legal/illegal transition; `isTerminal`; `paymentStatusFromCallback`.
- [x] `tests/payment-service.test.ts` (6 tests) — `/pay` flow, error propagation, terminal-state rejection, refund validation.
- [x] `tests/routes.test.ts` (7 tests) — `/health`, `/pay`, callback validation, HMAC on/off.
- [x] **All 25 tests pass.** `npm run typecheck` clean. `npm run build` produces `dist/`.

---

## Files Changed This Session

| Path                                       | Change | Notes                                                                   |
|--------------------------------------------|--------|-------------------------------------------------------------------------|
| `docs/AGENT_2_CONTEXT.md`                  | updated| Added §3 stack decision + §16 resolved unknowns.                         |
| `docs/AGENT_2_PROGRESS.md`                 | updated| This file.                                                               |
| `package.json`                             | created| Scripts: build, start, dev, test, test:watch, migrate, migrate:down, typecheck. |
| `tsconfig.json`                            | created| Strict, ES2022, CommonJS, outDir=dist.                                   |
| `.gitignore`                               | created| Node + build artefacts.                                                  |
| `.env.example`                             | created| All env vars documented.                                                |
| `vitest.config.ts`                         | created| Node env, esbuild target node20.                                         |
| `migrations/1700000000000_payments.sql`    | created| UNIQUE(payment_id) + UNIQUE(idempotency_key).                            |
| `migrations/1700000000001_payment_events.sql` | created | UNIQUE(event_id).                                                   |
| `src/config/env.ts`                        | created| Typed config + callback URL builder.                                     |
| `src/db/pool.ts`                           | created| Pool wrapper + `withTx`.                                                 |
| `src/payments/types.ts`                    | created| Domain types.                                                            |
| `src/payments/repository.ts`               | created| Race-safe `processCallbackInTx`.                                         |
| `src/payments/state.ts`                    | created| Forward-only FSM.                                                        |
| `src/payments/errors.ts`                   | created| Domain error classes.                                                    |
| `src/payments/service.ts`                  | created| Single owner of payment state.                                           |
| `src/ports/booking-payment.port.ts`        | created| Port + Noop adapter.                                                     |
| `src/ports/show-seat-payment.port.ts`      | created| Port + Noop adapter.                                                     |
| `src/gateway/hmac.ts`                      | created| `signBody` + `verifySignature` over raw bytes.                           |
| `src/gateway/client.ts`                    | created| HTTP client with timeouts, idempotency key, mock-force passthrough.      |
| `src/http/routes.ts`                       | created| All HTTP routes + error mappers.                                         |
| `src/app.ts`                               | created| Fastify builder with raw-body parser hookup.                              |
| `src/server.ts`                            | created| Boot + listen + graceful shutdown.                                       |
| `tests/hmac.test.ts`                       | created| 4 tests.                                                                 |
| `tests/state-machine.test.ts`              | created| 8 tests.                                                                 |
| `tests/payment-service.test.ts`            | created| 6 tests (with `vi.mock` for repository).                                  |
| `tests/routes.test.ts`                     | created| 7 tests via `app.inject`.                                                |

---

## Design Decisions (Locked)

1. **DB is the source of truth.** UNIQUE constraints (`payments.payment_id`, `payments.idempotency_key`, `payment_events.event_id`) are the dedup primitives. The application layer catches PG `23505` and translates it into the right outcome.
2. **`/pay` is async only.** Never hold a DB transaction across the gateway call. Gateway HTTP always happens outside `withTx`.
3. **Duplicate callback → HTTP 200, always.** No 409. The gateway would otherwise retry forever.
4. **`event_id` is the callback dedup key.** Insert into `payment_events`, catch `23505` → return `{ duplicate: true }` and stop.
5. **No direct mutation of `show_seats` by Agent 2.** Agent 2 calls `ShowSeatPaymentPort.bookForBooking` / `releaseForBooking` from inside the same callback transaction.
6. **HMAC is optional.** When `HMAC_ENABLED=true`, the callback route uses `config: { rawBody: true }` and the Fastify content-type parser captures the exact bytes for `verifySignature`.
7. **Race handling.** If the gateway callback arrives before `/pay` has created a row, the callback handler creates a minimal PENDING payment row with the gateway's `payment_id` so the callback can still be processed exactly-once.
8. **Refund policy.** Default MVP: refund only allowed on SUCCEEDED payments; REFUNDED state does not change booking/show_seat (no compensation logic). Documented for future iteration.

---

## Tests Run / Results

```
$ npm test

Γ£ô tests/hmac.test.ts            (4 tests)  4ms
Γ£ô tests/state-machine.test.ts   (8 tests)  3ms
Γ£ô tests/payment-service.test.ts (6 tests)  5ms
Γ£ô tests/routes.test.ts          (7 tests) 50ms

Test Files  4 passed (4)
Tests       25 passed (25)
Duration    ~1s
```

`npm run typecheck` — clean. `npm run build` — emits `dist/` with no errors.

---

## Current Problems / Blockers

None for Agent 2's local code path.

For full end-to-end runtime validation, the following are still needed (not Agent 2's responsibility to deliver alone):

1. **Agent 1 booking/seat real adapters** — currently the service uses `NoopBookingAdapter` and `NoopShowSeatAdapter`. Real wiring is a one-line swap in `src/server.ts` once Agent 1 publishes its modules.
2. **Real Postgres for live integration tests** — vitest currently exercises logic with `vi.mock`'d repository. Adding a `pg-mem` based integration test is feasible if needed, but the DB-uniqueness semantics are already covered by the SQL constraints and require no extra test code.
3. **Gateway binary / image** — once provided, the integration path is `makeGatewayClient(cfg).charge(...)` with `X-Mock-Force` for testing failure modes.

---

## Unresolved Questions (none blocking)

None. All unknowns documented in `AGENT_2_CONTEXT.md` §16 are resolved with documented assumptions.

---

## Dependencies on Agent 1

Agent 2's `src/server.ts` instantiates `NoopBookingAdapter` and `NoopShowSeatAdapter`. Agent 1 should swap these for real adapters implementing the same interfaces:

```ts
import { BookingPaymentPort } from './ports/booking-payment.port';
import { ShowSeatPaymentPort } from './ports/show-seat-payment.port';
// import { Agent1BookingAdapter } from '@agent-1/booking';
// import { Agent1ShowSeatAdapter } from '@agent-1/show-seats';

const bookings: BookingPaymentPort = new Agent1BookingAdapter(pgPool);
const seats: ShowSeatPaymentPort = new Agent1ShowSeatAdapter(pgPool);
```

---

## Dependencies on Agent 3

Agent 3 owns the frontend. It calls:

- `POST /api/bookings/{bookingRef}/pay`
- `POST /api/otp/send`
- `POST /api/otp/verify`
- `GET /api/payments/{paymentId}` (if Agent 3 surfaces status — not yet implemented; out of MVP scope unless requested)

`POST /api/payments/callback` is gateway-only; Agent 3 never calls it.

---

## Current Task

**Implementation complete.** Awaiting integration with Agent 1's booking/seat modules and the actual payment gateway.

---

## Phase 3 — Audit Fixes (H-1, H-2, H-3, H-7)

After a manager audit, four REQUIRED high-risk items were addressed. All four are verified locally (`npm run typecheck`, `npm run build`, `npm test` green at 34/34 unit tests; the 2-test integration suite is skipped unless `INTEGRATION_DATABASE_URL` is set).

### Source changes

- `src/payments/errors.ts` — added `PaymentAmountMismatchError` and `PaymentValidationError`.
- `src/payments/service.ts`:
  - Top-of-file JSDoc locks the MVP refund policy and the single-owner rule.
  - `pay()` consults `bookings.getAmountByRef`; throws `PaymentValidationError` if missing/zero/negative.
  - `pay()` uses the booking port's amount/currency when creating the pending row; asserts match on retry.
  - `handleCallback()` validates callback amount/currency against the row; signals `amountMismatch: true`, skips transition.
  - `applyRefunded()` JSDoc is explicit: NO booking/seat port calls.
  - `CallbackResult` carries `amountMismatch: boolean`.
- `src/ports/booking-payment.port.ts` — added `BookingAmount` interface and `getAmountByRef` to the port; `NoopBookingAdapter` accepts a `defaultAmount` option (null to simulate "no price").
- `src/http/routes.ts` — maps `PaymentValidationError` → 400 `PAYMENT_VALIDATION`; surfaces `amountMismatch` in the callback response body.

### Test changes

- `tests/payment-service.test.ts` — three new describe blocks (H-2: 5 tests, H-1: 3 tests, H-7: 1 test); the repository mock now reads `row.amount/currency` post-closure to mirror real amount-mismatch detection; `updatePaymentStatus` mock now updates the row.
- `tests/integration/payment-dedup.integration.test.ts` — NEW. Real-PG test that creates an isolated schema, applies both migrations, and verifies that two identical callbacks produce one `payment_events` row, one payment status transition, and exactly one `confirmByRef` call. Skipped unless `INTEGRATION_DATABASE_URL` is set.

### Verification commands

```powershell
npm run typecheck   # tsc --noEmit, clean
npm run build       # tsc emit to dist/, clean
npm test            # vitest run, 34 passed (2 integration skipped when no DB)
```

### Items deferred per manager

H-4 (HMAC body size cap), H-5 (per-route rawBody config validation), H-6 (lock-free row-lock waiting), H-8 (idempotency retry-after semantics), H-9 (dead-letter for permanently failed gateways). Deferred code paths remain untouched.