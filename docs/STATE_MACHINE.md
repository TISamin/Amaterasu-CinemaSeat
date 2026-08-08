# CinemaSeat State Machine

This document defines the allowed state transitions.

All agents must follow these transitions.

Do not invent new states without team agreement.

---

# 1. Seat State Machine

A ShowSeat has one of:

```text
AVAILABLE
HELD
BOOKED
```

---

## AVAILABLE → HELD

A user successfully acquires the seat.

Conditions:

```text
status = AVAILABLE
```

or:

```text
status = HELD
AND hold_expires_at < current time
```

Result:

```text
status = HELD
held_by = current user
hold_expires_at = current time + HOLD_TTL_SECONDS
```

A booking should be associated with this hold.

---

# 2. HELD → BOOKED

Allowed when payment succeeds.

Condition:

```text
payment status = SUCCEEDED
```

Result:

```text
ShowSeat = BOOKED
Booking = CONFIRMED
```

The transition must be protected against concurrent state changes.

---

# 3. HELD → AVAILABLE

Allowed when:

```text
hold_expires_at < current time
```

and payment has not successfully completed.

The seat becomes available for another user.

This may happen lazily during a new hold request.

A cleanup job may also perform the physical update.

---

# 4. HELD → AVAILABLE ON PAYMENT FAILURE

If payment fails and the booking policy allows immediate release:

```text
HELD
 ↓
payment FAILED
 ↓
AVAILABLE
```

The implementation must follow the agreed booking/payment policy.

Do not allow a failed payment to leave a permanently blocked seat.

---

# 5. BOOKED → ?

A BOOKED seat is final for normal booking flow.

Do NOT allow:

```text
BOOKED → HELD
```

Do NOT allow:

```text
BOOKED → AVAILABLE
```

unless an explicit refund/cancellation feature is implemented and agreed by the team.

For the hackathon MVP:

```text
BOOKED = final
```

---

# 6. Seat State Diagram

```text
                 ┌──────────────┐
                 │   AVAILABLE  │
                 └──────┬───────┘
                        │
                    hold request
                        │
                        ▼
                 ┌──────────────┐
                 │     HELD     │
                 └──────┬───────┘
                        │
              ┌─────────┴──────────┐
              │                    │
        payment success        hold expires
              │                    │
              ▼                    ▼
       ┌──────────────┐     ┌──────────────┐
       │    BOOKED    │     │   AVAILABLE  │
       └──────────────┘     └──────────────┘
              ▲
              │
        booking confirmed
```

---

# 7. Booking State Machine

Booking states:

```text
PENDING_PAYMENT
CONFIRMED
PAYMENT_FAILED
EXPIRED
```

---

## PENDING_PAYMENT → CONFIRMED

Triggered by gateway callback:

```text
status = SUCCEEDED
```

Result:

```text
Booking = CONFIRMED
ShowSeat = BOOKED
Payment = SUCCEEDED
```

These changes should be performed atomically where appropriate.

---

# 8. PENDING_PAYMENT → PAYMENT_FAILED

Triggered by:

```text
payment callback
status = FAILED
```

Result:

```text
Booking = PAYMENT_FAILED
```

The associated seat should be released according to the agreed seat-release policy.

---

# 9. PENDING_PAYMENT → EXPIRED

Triggered when:

```text
hold_expires_at < current time
```

and payment has not successfully completed.

Result:

```text
Booking = EXPIRED
ShowSeat = AVAILABLE
```

---

# 10. CONFIRMED

A CONFIRMED booking is final for the normal hackathon flow.

Do not allow:

```text
CONFIRMED → PENDING_PAYMENT
```

Do not confirm the same booking twice.

Duplicate callbacks must leave the state unchanged.

---

# 11. Payment State Machine

Payment states:

```text
PENDING
SUCCEEDED
FAILED
REFUNDED
```

---

## PENDING → SUCCEEDED

Gateway callback:

```text
status = SUCCEEDED
```

Then:

```text
Payment = SUCCEEDED
Booking = CONFIRMED
ShowSeat = BOOKED
```

---

# 12. PENDING → FAILED

Gateway callback:

```text
status = FAILED
```

Then:

```text
Payment = FAILED
Booking = PAYMENT_FAILED
```

Release the seat according to the booking policy.

---

# 13. SUCCEEDED → REFUNDED

If a refund is requested and the gateway returns:

```text
REFUNDED
```

then:

```text
Payment = REFUNDED
```

Refund behavior for the booking/seat must follow the project's explicitly implemented refund policy.

Do not invent additional refund states.

---

# 14. Duplicate Callback Rule

The gateway can send the same callback more than once.

The callback contains:

```text
event_id
```

The same event_id means the same event.

Processing:

```text
FIRST EVENT
    ↓
event_id not found
    ↓
record event
    ↓
process state transition
    ↓
HTTP 200
```

Duplicate:

```text
SAME event_id
    ↓
already recorded
    ↓
NO state transition
    ↓
HTTP 200
```

Never return a non-2xx response for a recognized duplicate callback.

---

# 15. Callback Race Rule

The gateway can send a callback before `/pay` finishes.

Therefore:

```text
/pay request
      │
      ├──────────────→ Gateway
      │                    │
      │                    │
      │             callback arrives
      │                    │
      ▼                    ▼
response later       /payments/callback
```

The system must remain correct regardless of this ordering.

Do not rely on the `/charge` HTTP response arriving before the callback.

---

# 16. Concurrency Rule

The most important invariant:

```text
ONE SHOWSEAT
    ↓
AT MOST ONE ACTIVE HOLDER
    ↓
AT MOST ONE CONFIRMED BOOKING
```

For:

```text
100 simultaneous hold requests
```

the state transition must be:

```text
AVAILABLE
     │
     ├── request 1 → HELD
     │
     ├── request 2 → REJECTED
     ├── request 3 → REJECTED
     ├── ...
     └── request 100 → REJECTED
```

Expected:

```text
SUCCESS = 1
REJECTED = 99
OVERSELL = 0
```

---

# 17. Expiration Rule

Expiration is determined by:

```text
hold_expires_at < current time
```

not by whether a cleanup process happened to run.

Therefore:

```text
HELD + expired
```

must behave as:

```text
AVAILABLE
```

when a new user attempts to acquire the seat.

---

# 18. Forbidden Transitions

The following are invalid:

```text
AVAILABLE → BOOKED
```

without a valid hold/payment flow.

```text
BOOKED → HELD
```

```text
BOOKED → AVAILABLE
```

in the normal MVP.

```text
CONFIRMED → PENDING_PAYMENT
```

```text
PAYMENT_FAILED → CONFIRMED
```

unless a new valid payment attempt is explicitly modeled.

```text
duplicate callback → second state transition
```

---

# 19. Final State Rules

The normal successful flow is:

```text
Seat:

AVAILABLE
   ↓
HELD
   ↓
BOOKED

Booking:

PENDING_PAYMENT
   ↓
CONFIRMED

Payment:

PENDING
   ↓
SUCCEEDED
```

The expired flow:

```text
Seat:

AVAILABLE
   ↓
HELD
   ↓
EXPIRED
   ↓
AVAILABLE

Booking:

PENDING_PAYMENT
   ↓
EXPIRED
```

The failed payment flow:

```text
Payment:

PENDING
   ↓
FAILED

Booking:

PENDING_PAYMENT
   ↓
PAYMENT_FAILED
```

These transitions form the shared state machine for the CinemaSeat system.