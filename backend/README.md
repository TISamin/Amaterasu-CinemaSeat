# CinemaSeat Backend — Agent 1 ownership

Modular Spring Boot 3.3 monolith (Java 21). Owns the booking correctness path.

## Build / run

```bash
# from repo root
docker compose up --build        # recommended (CI / judges)
```

For local iteration outside Docker, install Maven 3.9+ and run:

```bash
cd backend
mvn -DskipTests spring-boot:run
```

Required env: `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`,
`HOLD_TTL_SECONDS`. Defaults match the docker-compose stack.

## Key endpoints

| Method | Path                                            | Notes                                     |
|--------|-------------------------------------------------|-------------------------------------------|
| GET    | `/health`                                       | Fast; never calls gateway                 |
| GET    | `/api/movies`                                   |                                           |
| GET    | `/api/movies/{movieId}/shows`                   |                                           |
| GET    | `/api/shows/{showId}/seats`                     | Lazy-expires stale `HELD`                 |
| POST   | `/api/shows/{showId}/seats/{showSeatId}/hold`   | Atomic; `200` or `409`                    |
| GET    | `/api/bookings/{bookingRef}`                    |                                           |

Payment endpoints (`/pay`, `/payments/callback`, `/otp/*`) are Agent 2's territory.

## Interface for Agent 2

```java
com.cinemaseat.booking.BookingStateService
  - confirmBooking(bookingRef)
  - failPayment(bookingRef)
  - expireBooking(bookingRef)
```

Inject this bean in your payment callback handler. Each method is idempotent
and concurrency-safe — duplicate callbacks are no-ops.
