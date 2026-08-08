import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api.js';

// Polling-based booking status. No WebSockets — the spec says polling is
// acceptable and we are not allowed to invent endpoints or contracts.
const POLL_MS = 1500;

export default function Checkout() {
  const { bookingRef } = useParams();
  const navigate = useNavigate();
  const [booking, setBooking] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);

  async function refresh() {
    const r = await api.getBooking(bookingRef);
    if (!r.ok) {
      setErr(`Failed to load booking (${r.status})`);
      return;
    }
    setBooking(r.body);
    return r.body;
  }

  useEffect(() => {
    let cancelled = false;
    let timer = null;

    async function poll() {
      if (cancelled) return;
      const b = await refresh();
      if (cancelled) return;
      if (b && (b.status === 'CONFIRMED' || b.status === 'PAYMENT_FAILED' || b.status === 'EXPIRED')) {
        if (b.status === 'CONFIRMED') {
          navigate(`/bookings/${bookingRef}/confirmed`);
          return;
        }
        return;
      }
      timer = setTimeout(poll, POLL_MS);
    }
    poll();
    return () => { cancelled = true; if (timer) clearTimeout(timer); };
  }, [bookingRef, navigate]);

  async function pay() {
    setBusy(true);
    setErr(null);
    const r = await api.pay(bookingRef);
    setBusy(false);
    if (!r.ok) {
      setErr(`Payment initiation failed (${r.status})`);
      return;
    }
    // The /pay endpoint returns 202 quickly. We rely on the polling above
    // (or a manual refresh) to pick up the final state from the callback.
  }

  return (
    <div>
      <h1>Checkout</h1>
      {err && <p className="error">{err}</p>}
      {booking && (
        <div className="card">
          <p>Booking ref: <strong>{booking.bookingRef}</strong></p>
          <p>Show: {booking.showId} • Seat: {booking.seatId}</p>
          <p>Amount: {booking.amount}</p>
          <p>
            Booking status:{' '}
            <span className={`status ${booking.status}`}>{booking.status}</span>
            {booking.paymentStatus && (
              <>
                {' '}• Payment:{' '}
                <span className={`status ${booking.paymentStatus}`}>{booking.paymentStatus}</span>
              </>
            )}
          </p>
          {booking.status === 'PENDING_PAYMENT' && (
            <button onClick={pay} disabled={busy}>
              {busy ? 'Starting…' : 'Pay now'}
            </button>
          )}
          <p className="muted">
            Status updates automatically as the gateway callback arrives.
          </p>
        </div>
      )}
      {!booking && !err && <p className="muted">Loading…</p>}
    </div>
  );
}