# CinemaSeat Database Contract

This document defines the shared database model for CinemaSeat.

All agents must follow these concepts and constraints.

PostgreSQL is the source of truth for seat availability and booking state.

---

# 1. Tables

The core database contains:

```text
movies
theatres
screens
seats
shows
show_seats
bookings
payments
payment_events
```

---

# 2. movies

Stores movie information.

### Fields

```text
id
title
description
duration_minutes
poster_url
created_at
```

### Constraints

```text
PRIMARY KEY (id)
```

---

# 3. theatres

Stores cinema/theatre information.

### Fields

```text
id
name
location
created_at
```

### Constraints

```text
PRIMARY KEY (id)
```

---

# 4. screens

Represents screens inside a theatre.

### Fields

```text
id
theatre_id
name
created_at
```

### Relationships

```text
screens.theatre_id
        ↓
theatres.id
```

A theatre can contain multiple screens.

---

# 5. seats

Represents the physical seat layout of a screen.

### Fields

```text
id
screen_id
row_label
seat_number
created_at
```

Example:

```text
screen 1
A1
A2
A3
...
F12
```

### Relationships

```text
seats.screen_id
        ↓
screens.id
```

### Constraint

A physical seat must be unique within a screen.

Recommended:

```text
UNIQUE(screen_id, row_label, seat_number)
```

---

# 6. shows

Represents a movie playing on a specific screen at a specific time.

### Fields

```text
id
movie_id
screen_id
start_time
base_price
created_at
```

### Relationships

```text
shows.movie_id
        ↓
movies.id

shows.screen_id
        ↓
screens.id
```

---

# 7. show_seats

This is the MOST IMPORTANT table.

A ShowSeat represents:

> One physical seat for one specific show.

For example:

```text
Show 101 + F12
```

must correspond to exactly one ShowSeat.

### Fields

```text
id
show_id
seat_id
status
held_by
hold_expires_at
price
created_at
updated_at
```

### Status

Allowed values:

```text
AVAILABLE
HELD
BOOKED
```

---

# 8. ShowSeat Unique Constraint

There must never be multiple inventory rows for the same:

```text
show_id
seat_id
```

Use:

```text
UNIQUE(show_id, seat_id)
```

This is critical.

---

# 9. ShowSeat Concurrency

The database must enforce seat acquisition correctness.

Do not rely on:

```text
Java synchronized
frontend state
in-memory maps
application instance locks
```

Use PostgreSQL transaction/row-level concurrency.

An atomic conditional update is acceptable.

Conceptually:

```sql
UPDATE show_seats
SET
    status = 'HELD',
    held_by = ?,
    hold_expires_at = ?
WHERE id = ?
AND (
    status = 'AVAILABLE'
    OR (
        status = 'HELD'
        AND hold_expires_at < CURRENT_TIMESTAMP
    )
);
```

If one row is updated:

```text
SUCCESS
```

If zero rows are updated:

```text
SEAT_UNAVAILABLE
```

---

# 10. Hold TTL

The hold duration must come from:

```text
HOLD_TTL_SECONDS
```

Do not hardcode it in the database or Java code.

Example:

```text
HOLD_TTL_SECONDS=30
```

means:

```text
hold_expires_at = current time + 30 seconds
```

---

# 11. Expired Holds

A HELD seat with:

```text
hold_expires_at < current time
```

is considered expired.

Expired HELD seats must be reclaimable.

The system may physically update:

```text
HELD → AVAILABLE
```

or treat the expired row as effectively available during the next hold attempt.

Correctness must not depend on a scheduled cleanup process.

---

# 12. bookings

Represents a user's booking attempt.

### Fields

```text
id
booking_ref
show_seat_id
user_id
status
created_at
updated_at
```

### Status

```text
PENDING_PAYMENT
CONFIRMED
PAYMENT_FAILED
EXPIRED
```

### Constraints

```text
PRIMARY KEY(id)

UNIQUE(booking_ref)
```

A booking reference must identify exactly one booking.

---

# 13. Booking Relationship

```text
bookings.show_seat_id
        ↓
show_seats.id
```

One booking references one ShowSeat.

The same ShowSeat must not have multiple active/confirmed bookings.

The application/database logic must enforce this.

---

# 14. payments

Represents payment state for a booking.

### Fields

```text
id
payment_id
booking_ref
status
amount
currency
created_at
updated_at
```

### Status

```text
PENDING
SUCCEEDED
FAILED
REFUNDED
```

### Constraints

```text
UNIQUE(payment_id)
```

The gateway's payment_id must never produce multiple payment records.

---

# 15. payment_events

Stores processed gateway callbacks.

### Fields

```text
id
event_id
payment_id
booking_ref
status
amount
currency
received_at
```

### Critical constraint

```text
UNIQUE(event_id)
```

This is the callback idempotency mechanism.

---

# 16. Callback Idempotency

When a callback arrives:

```text
event_id = evt_123
```

Attempt to record it.

If the event does not already exist:

```text
INSERT event
→ process payment
```

If it already exists:

```text
duplicate
→ do not process again
→ return HTTP 200
```

The unique database constraint protects against concurrent duplicate callbacks.

---

# 17. Important Indexes

At minimum, index:

```text
show_seats.show_id
show_seats.seat_id
show_seats.status
show_seats.hold_expires_at

bookings.booking_ref

payments.payment_id
payments.booking_ref

payment_events.event_id
```

The exact indexes may be adjusted after inspecting actual queries.

---

# 18. Foreign Keys

Use foreign keys for:

```text
screens.theatre_id → theatres.id

seats.screen_id → screens.id

shows.movie_id → movies.id

shows.screen_id → screens.id

show_seats.show_id → shows.id

show_seats.seat_id → seats.id

bookings.show_seat_id → show_seats.id

payments.booking_ref → bookings.booking_ref
```

Use the repository's actual implementation conventions if the existing project already uses equivalent relationships.

---

# 19. Seed Data

The project must provide automatic seed data sufficient for demonstration.

At minimum:

- multiple movies
- at least one theatre
- at least one screen
- a usable seat layout
- multiple shows
- seat prices

Seed data must be created automatically when the application/database starts.

---

# 20. Migration Rules

Use the project's existing migration mechanism.

If Flyway is already used:

```text
V1__initial_schema.sql
V2__seed_data.sql
...
```

Do not manually alter production database structure outside migrations.

Every schema change must be reproducible from a clean database.

---

# 21. Database Invariants

These must ALWAYS remain true:

1. `(show_id, seat_id)` is unique in `show_seats`.
2. `booking_ref` is unique.
3. `payment_id` is unique.
4. `event_id` is unique.
5. A BOOKED ShowSeat cannot become HELD.
6. An active HELD ShowSeat cannot be acquired by another user.
7. Expired HELD seats can be reclaimed.
8. Duplicate payment events cannot be processed twice.

---

# 22. Source of Truth

PostgreSQL is the authoritative source for:

- seat state
- hold state
- booking state
- payment state
- callback event history

Do not use Redis or frontend state as the authoritative booking state.

The frontend is only a view of the database state.