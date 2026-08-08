# CinemaSeat

> Movie ticket booking platform built for the **Zero to Production Phase 2** hackathon.
> Modular Spring Boot monolith + PostgreSQL + provided mock payment gateway + minimal React SPA.

The full engineering specification lives in `docs/` and the master spec handed down by the judges. This README is the judge-facing summary.

---

## 1. Architecture

```
┌──────────────────────────────┐
│  Browser (React SPA, nginx)  │   port 8080
└──────────────┬───────────────┘
               │ /api/* (same origin, nginx reverse proxy)
               ▼
┌──────────────────────────────┐
│  Spring Boot API (api)       │   port 3000
│  - Catalog / Shows / Seats   │
│  - Hold (DB row-level lock)  │
│  - /pay (async)              │
│  - /api/payments/callback    │
└──────┬────────────────┬──────┘
       │                │
       │                │ http://gateway:9000
       ▼                ▼
┌──────────────┐  ┌────────────────────────────┐
│  PostgreSQL  │  │  Mock Payment Gateway       │   port 9000
│   (16)       │  │  asifmahmoud414/mock-       │
│              │  │  gateway:latest             │
└──────────────┘  └────────────────────────────┘
```

- **Postgres** is the source of truth for seat, booking, and payment state.
- **api ↔ gateway** traffic uses the Docker Compose service name `gateway` (NEVER `localhost` from inside containers).
- The gateway calls back to **`http://api:3000/api/payments/callback`** so the backend is reachable on the Compose network.

Detailed diagram: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## 2. Technology stack

| Layer        | Choice                                              |
| ------------ | --------------------------------------------------- |
| Frontend     | React 18 + Vite + react-router-dom (SPA)            |
| Frontend web | nginx (serves SPA + reverse-proxies `/api/*`)       |
| Backend      | Spring Boot (Java 21, Maven) — modular monolith     |
| DB           | PostgreSQL 16 (Flyway migrations + seed data)       |
| Payment gw.  | `asifmahmoud414/mock-gateway:latest` (provided)     |
| CI           | GitHub Actions (backend tests, frontend build, compose build) |
| Load test    | k6 (run from a separate machine, not the app host)  |

---

## 3. Local setup (clean clone)

```bash
git clone https://github.com/TISamin/Amaterasu-CinemaSeat.git
cd Amaterasu-CinemaSeat
cp .env.example .env
docker compose up --build
```

That is the entire setup. Postgres initializes via Flyway on backend boot, seed data is loaded automatically, the gateway starts with its default deterministic mode, and the frontend proxies `/api/*` to the backend on the Compose network.

### URLs after `docker compose up`

| What          | URL                                  |
| ------------- | ------------------------------------ |
| Frontend SPA  | http://localhost:8080                |
| Backend API   | http://localhost:3000                |
| Health        | http://localhost:3000/health         |
| Gateway       | http://localhost:9000                |

---

## 4. Environment variables

See `.env.example`. Required keys:

```
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
DATABASE_URL
HOLD_TTL_SECONDS
GATEWAY_URL
GATEWAY_SECRET
API_PORT
GATEWAY_PORT
FRONTEND_PORT
VITE_API_BASE_URL
```

`HOLD_TTL_SECONDS` is **never** hardcoded in the backend; the hold endpoint reads it from the environment.

---

## 5. API

The full contract is in [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md). Quick map:

| Method | Path                                       | Notes |
| ------ | ------------------------------------------ | ----- |
| GET    | `/health`                                  | Fast, gateway-independent |
| GET    | `/api/movies`                              | List movies |
| GET    | `/api/movies/{movieId}/shows`              | Showtimes for a movie |
| GET    | `/api/shows/{showId}/seats`                | Live seat map |
| POST   | `/api/shows/{showId}/seats/{seatId}/hold`  | Atomically acquire seat (DB row-level lock) |
| GET    | `/api/bookings/{bookingRef}`               | Current booking + payment state |
| POST   | `/api/bookings/{bookingRef}/pay`           | Returns 202 quickly; gateway decides final state |
| POST   | `/api/payments/callback`                   | Idempotent on `event_id` |
| POST   | `/api/otp/send`                            | Delegates to gateway |
| POST   | `/api/otp/verify`                          | Delegates to gateway |

### Seat statuses

`AVAILABLE` → `HELD` → `BOOKED`. Expired `HELD` is reclaimed lazily during the next hold attempt (the unique constraint + atomic conditional update is the source of truth — cleanup is optional).

### Booking statuses

`PENDING_PAYMENT` → `CONFIRMED` | `PAYMENT_FAILED` | `EXPIRED`.

### Payment statuses

`PENDING` → `SUCCEEDED` | `FAILED` | `REFUNDED`.

---

## 6. Exact requests (judge-facing)

These are copy-pastable.

### Hold a seat

```http
POST /api/shows/101/seats/501/hold
Content-Type: application/json

{
  "userId": "user-001"
}
```

Successful response (`200`):

```json
{
  "bookingRef": "BK-001",
  "showId": 101,
  "seatId": 501,
  "status": "PENDING_PAYMENT",
  "holdExpiresAt": "2026-08-08T10:30:30Z",
  "amount": 450
}
```

Conflict (`409`):

```json
{
  "error": "SEAT_UNAVAILABLE",
  "message": "Seat is already held or booked."
}
```

### Seat map

```http
GET /api/shows/101/seats
```

```json
[
  { "id": 501, "seatId": 101, "row": "A", "number": 1, "price": 450, "status": "AVAILABLE" },
  { "id": 502, "seatId": 102, "row": "A", "number": 2, "price": 450, "status": "HELD" },
  { "id": 503, "seatId": 103, "row": "A", "number": 3, "price": 450, "status": "BOOKED" }
]
```

### Initiate payment (returns 202 quickly)

```http
POST /api/bookings/BK-001/pay
```

```json
{ "bookingRef": "BK-001", "paymentId": "pay_abc123", "status": "PENDING" }
```

### Booking status (polled by the frontend)

```http
GET /api/bookings/BK-001
```

---

## 7. Deployed URL

> _Filled in after first successful deployment to Poridhi VM._

<!-- DEPLOYED_URL -->

---

## 8. Testing

### Backend (in `backend/`)

```bash
cd backend
mvn test
```

Covers seat state transitions (`AVAILABLE→HELD`, `HELD→rejected`, `BOOKED→rejected`, `expired HELD→new hold`), 100-concurrent hold assertions, payment idempotency, duplicate callback handling, race callback ordering, gateway failure modes.

### Frontend (in `frontend/`)

```bash
cd frontend
npm install
npm run build
```

### CI

`.github/workflows/ci.yml` runs backend tests, frontend build, and `docker compose build` on every PR and push to `main`.

---

## 9. Load tests (k6)

Run from a separate machine — never on the app host.

### Scenario A — 100 concurrent holds on the same seat

```bash
BASE_URL=https://<deployed-api>
SHOW_ID=101 SEAT_ID=501 \
  k6 run tests/load/scenario-a.js
```

Expected:

```
Requests    = 100
Successful  = 1
Rejected    = 99
Oversell    = 0
```

After the storm, `GET /api/shows/101/seats` must show the seat `HELD` exactly once.

**Result:** _to be filled in with observed numbers after the run._

### Scenario B — TTL expiration + re-hold

Set `HOLD_TTL_SECONDS=10` on the backend, then:

```bash
BASE_URL=https://<deployed-api>
SHOW_ID=101 SEAT_ID=501 TTL_SECONDS=10 \
  k6 run tests/load/scenario-b.js
```

Expected timeline:

1. User A holds seat → `200 PENDING_PAYMENT`.
2. Wait `TTL_SECONDS + 2s`.
3. User B holds same seat → `200 PENDING_PAYMENT` (hold expired and was reclaimed).

**Result:** _to be filled in with observed numbers after the run._

---

## 10. Known limitations

- The provided gateway is intentionally unreliable (~10% payment failure, ~8% duplicate callbacks, ~2% `/charge` 500, callbacks delayed 2–15s). The system is designed to absorb this; we do not replace the gateway.
- OTP delivery may silently fail. OTP is wired through the gateway but not a hard dependency of booking.
- HMAC signature verification is wired via `GATEWAY_SECRET` but treated as bonus — the seat/payment invariants do not depend on it.
- No refunds in the MVP. `BOOKED` and `CONFIRMED` are terminal states for the normal flow.
- Polling is used for booking status (no WebSocket). Spec explicitly allows polling.
- User identity is a hackathon-grade `userId` string; no real auth.

---

## 11. Repository layout

```
.
├── backend/                       (Agent 1 + Agent 2)
├── frontend/                      (Agent 3)
├── tests/load/
│   ├── scenario-a.js              (k6)
│   └── scenario-b.js              (k6)
├── docs/
│   ├── API_CONTRACT.md
│   ├── DATABASE_CONTRACT.md
│   ├── STATE_MACHINE.md
│   └── ARCHITECTURE.md
├── .github/workflows/ci.yml
├── docker-compose.yml
├── .env.example
├── README.md
└── DECISIONS.md
```