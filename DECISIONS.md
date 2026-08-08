# DECISIONS.md — CinemaSeat

Three engineering decisions that shaped the system. Each is documented with the options we considered, what we chose, why, and what we gave up.

---

## 1. Seat concurrency — database row-level locking vs Redis

**Options considered**

- **PostgreSQL row-level locking + atomic conditional UPDATE** on `show_seats`. The database is the source of truth. Hold acquisition uses either `SELECT … FOR UPDATE` inside a transaction or a single conditional `UPDATE … WHERE (status='AVAILABLE' OR (status='HELD' AND hold_expires_at < now()))`.
- **Redis distributed lock** (e.g. `SETNX` with a TTL) as the gate before touching Postgres. Fast, but introduces a second source of truth that must be kept consistent with the database.
- **In-process locks** (`synchronized` blocks, in-memory maps). Rejected outright — they do not survive horizontal scaling and cannot enforce cross-instance invariants.

**Chosen solution**

PostgreSQL row-level locking with an atomic conditional UPDATE, supplemented by `UNIQUE(show_id, seat_id)` on `show_seats` and a `UNIQUE` constraint on `bookings.booking_ref` / `payments.payment_id` / `payment_events.event_id`.

**Reason**

- Postgres is already a hard dependency of the system; adding Redis only to enforce a seat invariant adds an extra moving part for zero correctness gain.
- The conditional UPDATE is single-statement and atomic — exactly one of N concurrent attempts can flip the row, the rest observe zero rows updated. This is the textbook implementation of the requested invariant.
- Failure modes are simpler: there is no "what if Redis goes down" branch. Hold acquisition is correct as long as Postgres is correct.
- The schema constraints (`UNIQUE(show_id, seat_id)`, `UNIQUE(event_id)`) double as insurance even if application logic regresses.

**Tradeoffs / what we gave up**

- We gave up sub-millisecond hold latency. A DB roundtrip per hold is fine at hackathon scale and well within the 100-concurrent threshold.
- We gave up the option to use Redis for anything else (rate limiting, cache, pub/sub). If we later add caching, it must be acknowledged as a non-authoritative read cache.
- Lazy expiration relies on the conditional update on the next hold attempt. A separate scheduled cleanup is allowed but optional and never the only mechanism guaranteeing correctness.

---

## 2. Architecture — modular monolith vs microservices

**Options considered**

- **Microservices**: separate services for catalog, booking, payment, seat, each with its own database or shared database. Independent scaling and deployment boundaries.
- **Modular monolith**: single Spring Boot application with clear package boundaries (`catalog`, `booking`, `payment`, `web`), a single PostgreSQL database, single deployable unit.
- **Single-process with no module separation**: everything thrown together. Rejected — makes correctness reviews harder.

**Chosen solution**

Modular monolith on Spring Boot.

**Reason**

- The most important invariant — "one ShowSeat, at most one active holder, at most one confirmed booking" — is a database invariant. Splitting it across services does not move the work; it just adds network hops and distributed-transaction semantics.
- The provided gateway is asynchronous and the only "external" dependency. There is no operational reason to split payment into its own service.
- A monolith is faster to ship, easier to test end-to-end, and matches the hackathon grading criteria ("smallest system that can honestly defend the invariant").
- Module boundaries in the package layout keep the codebase reviewable, and a future microservice split remains possible if a real boundary emerges.

**Tradeoffs / what we gave up**

- We gave up independent deployability of catalog vs booking vs payment. For this hackathon that is irrelevant; for a production system it would be a real cost.
- We gave up language polyglot. Everything is Java + Spring. This is fine for the spec and removes an entire class of integration bugs.
- We gave up per-module horizontal scaling. Acceptable — the API itself scales horizontally; modules share the same JVM but the contention point is the database row, not the app tier.

---

## 3. Live updates — polling vs WebSocket

**Options considered**

- **WebSocket / Server-Sent Events** for pushing booking-status changes to the browser as soon as the gateway callback lands.
- **Short-interval polling** of `GET /api/bookings/{bookingRef}` from the React checkout page.
- **One-shot refresh only** (no live updates) — user manually reloads.

**Chosen solution**

Polling, ~1.5s interval, with an automatic redirect to `/bookings/:ref/confirmed` when the booking transitions out of `PENDING_PAYMENT`.

**Reason**

- The spec explicitly says polling is acceptable and we must not introduce WebSockets unless the team agrees. The gateway callbacks are inherently delayed 2–15s anyway, so a sub-2s poll comfortably catches the transition.
- Polling is stateless, works through any reverse proxy, requires no extra Spring infrastructure (`WebSocketConfigurer`, sticky sessions, auth on the upgrade), and falls back gracefully if the user is on a flaky connection.
- The frontend code stays simpler — a single `setTimeout` loop in `Checkout.jsx`. No reconnection logic, no event framing, no client library.

**Tradeoffs / what we gave up**

- We gave up instant UI feedback on confirmation. The user sees "Payment Pending" for up to ~1.5s after the callback is processed server-side. Acceptable.
- We gave up server-push efficiency: N open browsers polling produce N requests every 1.5s. At hackathon scale this is noise; at production scale this would need HTTP/2 SSE or a notification service.
- We gave up the ability to surface transient gateway events (e.g. "callback received, validating…") in real time. The status response only shows terminal-ish snapshots.

---

## Summary

| Decision                     | Chosen                              | Given up                              |
| ---------------------------- | ----------------------------------- | ------------------------------------- |
| Seat concurrency             | PostgreSQL row-level lock + atomic  | Redis as second source of truth       |
| Architecture                 | Modular Spring Boot monolith        | Independent service deployability     |
| Live updates                 | Frontend polling (~1.5s)            | WebSocket / SSE instant push          |

These three choices were made because the spec values **correctness under concurrency** and **payment reliability** above visual polish or feature breadth.