# CinemaSeat — Architecture

This is a living document maintained by the integration owner (Agent 3).
It does not redefine API or DB contracts — see `API_CONTRACT.md` and `DATABASE_CONTRACT.md` for those.

---

## 1. High-level topology

```
┌──────────────────────────────────────────────────────────────┐
│                        Browser (SPA)                         │
│   React + Vite + react-router-dom, served as static assets   │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTP / same origin (nginx reverse proxy)
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                Frontend container (nginx)                    │
│   - Serves /usr/share/nginx/html (SPA)                      │
│   - Reverse-proxies /api/*  →  http://api:8080               │
└──────────────────────────┬───────────────────────────────────┘
                           │ Docker Compose internal network
                           ▼
┌──────────────────────────────────────────────────────────────┐
│            api container — Spring Boot (Java 21)            │
│   Modules: catalog, booking, payment, web                   │
│                                                              │
│   POST /api/shows/:id/seats/:seatId/hold                    │
│       - atomic conditional UPDATE on show_seats             │
│       - row-level lock via SELECT … FOR UPDATE              │
│                                                              │
│   POST /api/bookings/:ref/pay  →  gateway /charge           │
│       - returns 202 quickly                                 │
│                                                              │
│   POST /api/payments/callback                               │
│       - idempotent on event_id (UNIQUE constraint)          │
└──────────┬──────────────────────────────────┬────────────────┘
           │                                  │
           │ jdbc:postgresql://postgres:5432  │ http://gateway:9000
           ▼                                  ▼
┌────────────────────────┐    ┌──────────────────────────────────┐
│ postgres:16-alpine     │    │  asifmahmood414/mock-gateway     │
│   - Flyway migrations  │    │   - POST /charge                 │
│   - V2 seed data       │    │   - POST /otp/send, /otp/verify  │
│   - UNIQUE constraints │    │   - async callback to:          │
│     (show_id,seat_id)  │    │       http://api:8080/api/       │
│     booking_ref        │    │       payments/callback          │
│     payment_id         │    └──────────────────────────────────┘
│     event_id           │
└────────────────────────┘
```

---

## 2. Critical correctness boundaries

| Boundary                                          | Mechanism                                  |
| ------------------------------------------------- | ------------------------------------------ |
| One ShowSeat, at most one active holder           | `UNIQUE(show_id, seat_id)` + atomic UPDATE |
| One booking per booking reference                 | `UNIQUE(bookings.booking_ref)`             |
| One payment record per gateway `payment_id`       | `UNIQUE(payments.payment_id)`              |
| Callback processed at most once                   | `UNIQUE(payment_events.event_id)`          |
| /pay returns quickly                              | Gateway `/charge` called outside DB tx     |
| /health stays up if gateway is down               | No synchronous gateway call in /health     |

---

## 3. Network flows

### 3.1 Hold a seat

```
Browser
  → POST /api/shows/101/seats/501/hold   (via nginx /api proxy)
  → api:   BEGIN
            SELECT … FROM show_seats WHERE id = ? FOR UPDATE
            — guard expired HELD —
            UPDATE show_seats SET status='HELD', held_by=?, hold_expires_at=?
            INSERT INTO bookings(booking_ref, show_seat_id, …, status='PENDING_PAYMENT')
          COMMIT
  → 200 { bookingRef, holdExpiresAt, amount }
```

If the UPDATE affects 0 rows → `409 SEAT_UNAVAILABLE`.

### 3.2 Pay

```
Browser
  → POST /api/bookings/BK-001/pay
  → api: load booking, validate hold is still active
         POST  http://gateway:9000/charge    (with Idempotency-Key)
         INSERT payment(payment_id, status='PENDING')    — or UPSERT if race
  → 202 { bookingRef, paymentId, status:'PENDING' }
  → (transaction has already committed; gateway reply is NOT held open)
```

### 3.3 Callback

```
gateway → POST /api/payments/callback  (raw body, may include X-Signature)
  → api: BEGIN
         INSERT INTO payment_events(event_id, …)   -- UNIQUE catches dupes
         on duplicate key → COMMIT and return 200 immediately
         else:
            UPSERT payment  by  payment_id
            UPDATE booking  by  booking_ref
            if status='SUCCEEDED':
                UPDATE show_seats SET status='BOOKED'  WHERE id=?
                UPDATE booking  SET status='CONFIRMED'
            elif status='FAILED':
                UPDATE booking  SET status='PAYMENT_FAILED'
                UPDATE show_seats SET status='AVAILABLE'
          COMMIT
  → 200
```

### 3.4 Callback-before-`/pay` race

The `/pay` path inserts the payment row using an UPSERT keyed on `payment_id`, so even if the callback lands before the `/pay` transaction commits, the callback's `UPDATE booking` and `UPDATE show_seats` will see consistent state once `/pay` finishes (or vice-versa). This is the documented "race" handling and is covered by the `X-Mock-Force: race` test scenario.

---

## 4. Environment / secrets

All configuration via environment variables. No secrets are committed. See `.env.example`.

| Variable             | Used by                | Notes                                        |
| -------------------- | ---------------------- | -------------------------------------------- |
| `DATABASE_URL`       | api                    | jdbc:postgresql://postgres:5432/cinemaseat   |
| `POSTGRES_*`         | postgres container     | bootstraps the DB                            |
| `HOLD_TTL_SECONDS`   | api (hold + expiration | never hardcoded                              |
| `GATEWAY_URL`        | api                    | `http://gateway:9000` — service name         |
| `GATEWAY_SECRET`     | api                    | HMAC verification (bonus)                    |
| `CALLBACK_URL`       | api → gateway          | `http://api:8080/api/payments/callback`      |

---

## 5. Frontend / backend integration

The browser never speaks to Postgres or the gateway. It talks to the backend through `/api/*`, which nginx in the frontend container proxies to `http://api:8080`. This:

- avoids CORS preflights,
- keeps the backend hostname abstract,
- makes local development (Vite proxy) and Docker (nginx proxy) behave identically.

The full frontend flow is documented in `README.md` §5 and matches `docs/API_CONTRACT.md` exactly — no invented endpoints.