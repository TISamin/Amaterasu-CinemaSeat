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

---

## 9. Handoff Status (end of Agent 1's session)

### What builds and passes (verified on this host)

| Suite | Class | Result |
|---|---|---|
| Service-layer unit tests | `BookingStateServiceTest` | **5/5 green** |
| Concurrency primitive (Java simulation) | `ShowSeatConcurrencyUnitTest` | **1/1 green — exactly 1 winner / 99 losers for 100 racers** |
| Production code (`mvn -DskipTests compile`) | all `src/main/java` | **BUILD SUCCESS** |
| Test compile | all `src/test/java` | **BUILD SUCCESS** |

### What does NOT run on this host

- `ShowSeatConcurrencyIT`, `ShowSeatHoldIT`, `SeatMapLazyExpirationIT` — Testcontainers-backed; need either Docker (none on host) or a real Postgres on `localhost:5432` (also none — EDB installer did not write to `Program Files` under the current UAC). These **must** be run by Agent 3 in the CI container (`mvn -Dtest='*IT' test`) before declaring the booking invariant green.

### Files added (in `feature/core-booking`)

- `backend/pom.xml` — Spring Boot 3.3.4 parent, Java 21, deps: web/data-jpa/validation/actuator/flyway/postgresql + test deps: testcontainers (postgresql, junit-jupiter), spring-boot-testcontainers
- `backend/src/main/resources/application.yml` — env-driven port + datasource + Flyway + `hold.ttl-seconds`
- `backend/src/main/resources/db/migration/V1__schema.sql` — Agent 1 schema (tables, constraints, indexes, FKs)
- `backend/src/main/resources/db/migration/V2__seed.sql` — 4 movies, 2 theatres, 3 screens, 60 seats (A–F × 10), 5 shows, all `show_seats` AVAILABLE
- `backend/src/main/java/com/cinemaseat/` — package layout: `movie`, `theatre`, `screen`, `seat`, `show`, `showseat`, `booking`, `config`, `health`, `web` (`CinemaSeatApplication.java`)
- Entities: `Movie`, `Theatre`, `Screen`, `Seat`, `Show`, `ShowSeat`, `Booking` (JPA, snake_case columns, enums via `EnumType.STRING`)
- Enums: `SeatStatus { AVAILABLE, HELD, BOOKED }`, `BookingStatus { PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, EXPIRED }`
- Repositories: 7 repos; `ShowSeatRepository` exposes atomic `tryHold / confirmHold / releaseHold` native UPDATEs; `BookingRepository` exposes `markConfirmed / markPaymentFailed / markExpired` (each gated on `status='PENDING_PAYMENT'` for idempotency)
- Service layer: `HoldProperties` (reads `hold.ttl-seconds`), `BookingStateService` + `BookingStateServiceImpl` (Agent 2 contract), `BookingRefGenerator` (`BK-` + 10 base32), `HoldResult` record, `SeatHoldService` (transactional hold + booking insert)
- DTOs: `MovieDto`, `ShowDto`, `SeatDto`, `HoldRequest`, `HoldResponse`, `BookingDto`, `ErrorResponse`
- Controllers: `MovieController`, `ShowController`, `SeatMapController` (lazy expiration), `HoldController` (200 / 409), `BookingStatusController`, `HealthController` (independent of gateway)
- Tests: 3 Testcontainers-backed integration tests + 2 host-runnable unit tests (Mockito + pure-Java concurrency simulation)

### Critical concurrency note (read this, Agent 2)

The **only** thing keeping the no-oversell guarantee is the atomic conditional UPDATE in `ShowSeatRepository#tryHold`:

```sql
UPDATE show_seats
SET status='HELD', held_by=:userId, hold_expires_at=:now+ttl
WHERE id=:showSeatId
  AND show_id=:showId
  AND (status='AVAILABLE'
       OR (status='HELD' AND hold_expires_at < CURRENT_TIMESTAMP))
```

The UPDATE returns a rowcount. `rowcount==1` → success. Anything else → `409 SEAT_UNAVAILABLE`. The Postgres row-level lock during conflicting concurrent UPDATEs guarantees exactly one winner. **Do not wrap this in a Java lock, do not add `@Version`, do not read-then-write** — all of those reintroduce races.

### Endpoints ready for Agent 3 to call

| Method | Path | Notes |
|---|---|---|
| GET | `/health` | `{status:UP}`, gateway-independent |
| GET | `/api/movies` | list |
| GET | `/api/movies/{movieId}/shows` | upcoming shows only |
| GET | `/api/shows/{showId}/seats` | lazy-expires HELD+past |
| POST | `/api/shows/{showId}/seats/{showSeatId}/hold` | body `{"userId":"..."}` → 200 `HoldResponse` or 409 `ErrorResponse` |
| GET | `/api/bookings/{bookingRef}` | status + payment status string |

### What Agent 2 should `cd backend && grep -n "TODO\|FIXME" src` for

No TODOs left in Agent 1 code. The `BookingStateService` interface in `com.cinemaseat.booking` is the **only** thing you call from your callback handler — it does everything you need idempotently:

```java
@PostMapping("/api/payments/callback")
public ResponseEntity<?> onCallback(@RequestBody GatewayPayload p) {
    switch (p.status()) {
        case SUCCEEDED  -> bookingStateService.confirmBooking(p.bookingRef());
        case FAILED     -> bookingStateService.failPayment(p.bookingRef());
        case EXPIRED    -> bookingStateService.expireBooking(p.bookingRef());
    }
    return ResponseEntity.ok();   // duplicate callback → 200 with no side effect
}
```

### What Agent 3 needs from this branch

- Maven is installed at `E:\Maven\apache-maven-3.9.9\` on this Windows host. PATH was updated for current session and persisted to user env. The Docker image should bundle its own Maven anyway.
- Postgres 16 binary downloaded but not installed (installer run silently but did not write to `Program Files`). Your `docker-compose.yml` will use `postgres:16-alpine` — that image is sufficient; you do not need a host install.
- `feature/core-booking` is committed (see `git log` for `feat(agent1): backend scaffold...`).
- Tests under `src/test/java/com/cinememaseat/showseat/*IT.java` and `src/test/java/com/cinememaseat/show/SeatMapLazyExpirationIT.java` need Docker. Run in CI.
- Unit tests (`BookingStateServiceTest`, `ShowSeatConcurrencyUnitTest`) run on any host with Maven.

### Final status

- Code: ✅ on disk + committed
- Compile: ✅ verified on host
- Unit tests: ✅ 6/6 green on host
- Integration tests (100-concurrent, lazy-expire, end-to-end): ⚠️  written, not yet executed (need Docker / Postgres) — Agent 3's responsibility in CI
---

## 10. Agent 2 Handoff (payment module)

**Goal**: Payment lifecycle correctness. Database, not Java, protects idempotency.

### What was built

| Layer | Files | Purpose |
|---|---|---|
| Migration | `db/migration/V3__payments.sql` | `payments` + `payment_events` tables; UNIQUE(payment_id), UNIQUE(event_id), FK booking_ref, CHECK status, indexes |
| Domain | `payment/Payment.java`, `PaymentEvent.java`, `PaymentStatus.java` | JPA entities, enum |
| Repos | `payment/PaymentRepository.java`, `PaymentEventRepository.java` | `findByBookingRef`, `findByPaymentId`, `markTerminalIfPending` (gated UPDATE), `findByEventId` |
| Gateway | `payment/gateway/GatewayClient.java` (interface), `RestGatewayClient.java` (impl), 6 DTOs, `GatewayException` | `charge` + OTP forwarding via Spring `RestClient` |
| Service | `payment/PaymentService.java` | `pay()` + `handleCallback()` + inner `GatewayCallbackPayload` record |
| Web | `payment/web/PaymentController.java`, `PaymentCallbackController.java`, `OtpController.java`, `PaymentExceptionHandler.java`, 4 DTOs | REST surface + scoped `@RestControllerAdvice` |
| Config | `config/GatewayProperties.java` | `gateway.url`, `gateway.secret`, `gateway.timeout-ms` |

### Idempotency strategy

Two layers:

1. **At the service layer** — `PaymentService.handleCallback()`:
   - Fast-path: `events.findByEventId(payload.eventId())` — if hit, return.
   - Otherwise `INSERT` into `payment_events` (UNIQUE(event_id) is the canonical guard). Catch `DataIntegrityViolationException` for race-loser.

2. **At the data layer** — `PaymentRepository.markTerminalIfPending(paymentId, newStatus)` — conditional UPDATE filtered on `status='PENDING'`. So `SUCCEEDED` followed by `REFUNDED` updates the row; `SUCCEEDED` twice is a no-op.

### Endpoint additions

| Method | Path | Status | Notes |
|---|---|---|---|
| POST | `/api/bookings/{bookingRef}/pay` | 202 | Idempotent re-entry returns existing row |
| POST | `/api/payments/callback` | 200 (always) | Defensive parsing; bad payload → 200 (don't poison gateway retry) |
| POST | `/api/otp/send` | 200 | Best-effort gateway call; returns `{sent: bool}` |
| POST | `/api/otp/verify` | 200 | Best-effort; returns `{verified: bool}` |

### Configuration

| Property | Default | Notes |
|---|---|---|
| `gateway.url` | `http://gateway:9000` | Docker service name. Local dev override: `http://localhost:9000` |
| `gateway.secret` | (empty) | If set, sent as `X-Gateway-Secret` header |
| `gateway.timeout-ms` | `3000` | Read by `GatewayProperties`, applied to RestClient |

### Decisions Made In This Session

| # | Decision | Chosen | Why |
|---|---|---|---|
| D6 | Gateway abstraction | `GatewayClient` interface + `RestGatewayClient` impl | Mockito couldn't mock the original concrete class via inline mocker; interface sidesteps it AND is cleaner design |
| D7 | Mock for `ShowSeat` | `ReflectionTestUtils.setField(new ShowSeat(), "price", X)` | Entity has no setters; `mock(ShowSeat.class)` triggers same ByteBuddy issue as `GatewayClient` did |
| D8 | Callback error policy | Always 200, even on bad payload | Avoid gateway retry loops / poison messages; log at WARN, never escalate |
| D9 | REFUNDED callback | Update payment row only; do NOT touch booking | STATE_MACHINE §13 explicit: refunds don't release seats in MVP |

### Coordination Notes For Agent 3

- **Backend endpoint table** is now `/api/bookings/{ref}/pay`, `/api/payments/callback`, `/api/otp/send`, `/api/otp/verify`. Add these to your frontend API client.
- **Gateway URL**: in Docker compose, set `GATEWAY_URL=http://gateway:9000`. In local dev, override with `GATEWAY_URL=http://localhost:9000`.
- **Callback URL inside Docker**: must be reachable from the `gateway` container. If your compose puts the backend on a different hostname (e.g. `api` instead of `backend`), adjust `callbackUrl` in `PaymentService.pay()`. Currently hardcoded to `/api/payments/callback` (relative path).
- **No new migrations** beyond V3 — Agent 3 can extend with V4 if needed (e.g. refunds table).
- **Unit test growth**: `PaymentServiceTest` adds 10 tests, bringing total to **16 unit tests** (5 + 10 + 1). All run without Docker.

### Final status

- Code: ✅ on disk + compile clean
- Unit tests: ✅ 16/16 green on host (`PaymentServiceTest` 10/10 + `BookingStateServiceTest` 5/5 + `ShowSeatConcurrencyUnitTest` 1/1)
- Integration tests: still need Docker / Postgres — Agent 3's CI responsibility
- Branch: TBD (Agent 2 did not commit/push in this session — awaiting merge-of-Agent-1 first per §7)
