# CinemaSeat — Team Context Log

> Living document updated after every meaningful chat turn.
> Anchors shared understanding for the whole team across sessions.

---

## 1. Project At A Glance

- **Repo**: `https://github.com/TISamin/Amaterasu-CinemaSeat.git`
- **Event**: Zero to Production — Phase 2, **CUET, 8 Aug 2026, 9 AM – 5 PM**
- **Track**: Movie ticket booking system that never double-books a seat
- **Architecture (mandated)**: **modular Spring Boot monolith** + PostgreSQL + provided mock gateway
- **Forbidden**: microservices for movie/booking/payment/seat, custom gateway mocks, Redis-as-source-of-truth

---

## 2. Team Ownership

| Agent | Owns | Does NOT touch |
|---|---|---|
| **Agent 1** (me) | DB schema, Flyway, Movie/Theatre/Screen/Seat/Show/ShowSeat/Booking, hold logic, concurrency, booking APIs, health | payment, gateway client, OTP, frontend, Docker, CI, deploy |
| **Agent 2** | payment module, gateway client, payment state machine, callback, idempotency, OTP, gateway failure handling, race handling | my DB migrations (we coordinate), frontend, Docker, CI |
| **Agent 3** | React UI, Dockerfiles, docker-compose.yml, GitHub Actions, deploy, k6, README, DECISIONS.md, architecture diagram, integration | my Java packages (only via APIs) |

---

## 3. Shared Contracts (already on `main`)

These three files are the **source of truth**. Touched only by team agreement.

- `docs/API_CONTRACT.md` — every endpoint, DTO shape, status code, error format
- `docs/DATABASE_CONTRACT.md` — every table, column, constraint, index, migration rule
- `docs/STATE_MACHINE.md` — allowed/forbidden state transitions for Seat / Booking / Payment

Critical invariants from the contracts:

1. `UNIQUE(show_id, seat_id)` in `show_seats` — never duplicate inventory
2. `UNIQUE(booking_ref)`, `UNIQUE(payment_id)`, `UNIQUE(event_id)`
3. Seat concurrency via **atomic conditional UPDATE**, not Java locks
4. `HOLD_TTL_SECONDS` from env, never hardcoded
5. `/health` must not call gateway; stays 200 if gateway is down
6. `/pay` must return fast; gateway callback is the source of final state
7. Duplicate callback → HTTP 200, no second transition

---

## 4. Current Repo State (as of session start)

```
Amaterasu-CinemaSeat/
├── .git/
├── .puku/
├── docs/
│   ├── API_CONTRACT.md
│   ├── DATABASE_CONTRACT.md
│   └── STATE_MACHINE.md
└── Untitled Project/        # empty, leftover from VS Code workspace — safe to delete
```

- No backend, frontend, Docker, or CI yet.
- Contracts are **complete and consistent** — no contract changes are proposed right now.
- Branch convention from spec: `feature/core-booking`, `feature/payment-gateway`, `feature/frontend-devops`. Agent 3 is integration owner.

---

## 5. Agent 1 Plan (this session)

**Goal**: Booking correctness. The database, not Java, protects the seat invariant.

### Order of work

1. `context.md` — this file ✅
2. Backend scaffold (Spring Boot 3.x, Maven, Java 21, JPA, Flyway, Postgres driver, JUnit)
3. Flyway `V1__schema.sql` — Agent 1 tables only
4. Flyway `V2__seed.sql` — movies / theatre / screen / seats / shows / show_seats
5. Entities + repositories (incl. atomic hold query)
6. Catalog APIs (`/api/movies`, shows, seat map with lazy expiration)
7. Hold service + booking service + controllers
8. `/health`
9. Unit + concurrency tests (incl. 100-concurrent-same-seat)
10. Interfaces for Agent 2 (`BookingStateService`)
11. Handoff report

### Concurrency strategy

- Single atomic UPDATE per `DATABASE_CONTRACT §9`:
  ```sql
  UPDATE show_seats
  SET status='HELD', held_by=:userId, hold_expires_at=:now+ttl
  WHERE id=:id
    AND (status='AVAILABLE'
         OR (status='HELD' AND hold_expires_at < CURRENT_TIMESTAMP))
  ```
- `rowcount == 1` → success, create `booking` row `PENDING_PAYMENT`, return `200`.
- `rowcount == 0` → `409 SEAT_UNAVAILABLE`.
- Booking insert happens **in the same transaction** as the UPDATE so a lost row cannot leave a held seat without a booking.

### Expiration strategy

- **Lazy**: seat-map endpoint treats `HELD && hold_expires_at < now` as `AVAILABLE`.
- Hold endpoint will reclaim expired holds on the fly via the same atomic UPDATE.
- Optional scheduler may run for cleanliness, but correctness must NOT depend on it.

### Interface I will expose for Agent 2

```java
public interface BookingStateService {
    void confirmBooking(String paymentId);     // PENDING_PAYMENT → CONFIRMED, HELD → BOOKED
    void failPayment(String paymentId);        // PENDING_PAYMENT → PAYMENT_FAILED, HELD → AVAILABLE
    void expireBooking(String bookingRef);     // PENDING_PAYMENT → EXPIRED, HELD → AVAILABLE
}
```

Agent 2 owns `payments` and `payment_events` tables and the callback endpoint. They will call this interface from their payment-confirm listener.

---

## 6. Decisions Made In This Session

| # | Decision | Chosen | Why |
|---|---|---|---|
| D1 | Concurrency primitive | Atomic conditional `UPDATE` on `show_seats` | One round-trip, no Java locks, race-safe by DB |
| D2 | Booking ↔ seat atomicity | Hold UPDATE + booking INSERT in one transaction | Eliminates "held but no booking" gap |
| D3 | Expiration | Lazy on read + reclaim-on-hold | Scheduler-free correctness |
| D4 | Health endpoint scope | DB-light `UP` status; no gateway call | Survives gateway outage per spec §22 |
| D5 | Test scope for Agent 1 | Unit + 100-concurrent-same-seat | Required by spec §26 + Milestone 4 Scenario A |

---

## 7. Coordination Notes For Other Agents

- **Agent 2**: Add your Flyway migrations as `V3__payments.sql` and `V4__payment_events.sql`. Use my `BookingStateService` from callback handler. The gateway callback URL inside Docker must be `http://api:<port>/api/payments/callback` — never `localhost`.
- **Agent 3**: Backend will expose `/health` at root, business APIs under `/api`. The `feature/core-booking` branch will be ready for you to merge. Run `docker compose up` from a clean clone and the seed will populate movies/theatre/shows.

---

## 8. Open Questions / Blockers

- _None yet_. If Agent 2's payment flow needs DB columns I don't have (e.g. on `bookings`), raise it before coding.