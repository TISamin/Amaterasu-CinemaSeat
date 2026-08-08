import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api.js';

export default function Confirmation() {
  const { bookingRef } = useParams();
  const [booking, setBooking] = useState(null);
  const [err, setErr] = useState(null);

  useEffect(() => {
    api.getBooking(bookingRef).then((r) => {
      if (!r.ok) return setErr(`Failed to load booking (${r.status})`);
      setBooking(r.body);
    });
  }, [bookingRef]);

  return (
    <div>
      <h1>Booking confirmed ✅</h1>
      {err && <p className="error">{err}</p>}
      {booking && (
        <div className="card">
          <p>Booking ref: <strong>{booking.bookingRef}</strong></p>
          <p>Show: {booking.showId}</p>
          <p>Seat: {booking.seatId}</p>
          <p>Amount: {booking.amount}</p>
          <p>
            Status:{' '}
            <span className={`status ${booking.status}`}>{booking.status}</span>
            {booking.paymentStatus && (
              <>
                {' '}• Payment:{' '}
                <span className={`status ${booking.paymentStatus}`}>{booking.paymentStatus}</span>
              </>
            )}
          </p>
        </div>
      )}
    </div>
  );
}