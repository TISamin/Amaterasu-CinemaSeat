import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api.js';

// Generate a stable per-browser userId. The hackathon contract says the
// backend identifies the user via userId in the hold body. We do not invent
// any auth; we just need a stable label per browser for the hold request.
function getUserId() {
  let id = localStorage.getItem('cinemaseat:userId');
  if (!id) {
    id = 'user-' + Math.random().toString(36).slice(2, 10);
    localStorage.setItem('cinemaseat:userId', id);
  }
  return id;
}

export default function SeatMap() {
  const { showId } = useParams();
  const navigate = useNavigate();
  const [seats, setSeats] = useState([]);
  const [selected, setSelected] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);

  const reload = () => {
    api.getSeatMap(showId).then((r) => {
      if (!r.ok) return setErr(`Failed to load seats (${r.status})`);
      setSeats(r.body || []);
    });
  };

  useEffect(reload, [showId]);

  const grouped = useMemo(() => {
    const byRow = {};
    for (const s of seats) {
      (byRow[s.row] ||= []).push(s);
    }
    for (const r of Object.keys(byRow)) {
      byRow[r].sort((a, b) => a.number - b.number);
    }
    return byRow;
  }, [seats]);

  async function hold() {
    if (!selected) return;
    setBusy(true);
    setErr(null);
    // Agent 1's hold endpoint is `/api/shows/{showId}/seats/{showSeatId}/hold`
    // and the {showSeatId} path variable is the show_seats.id (the same id
    // returned in SeatMap JSON under `id`), NOT the seat-definition id (which
    // is the seat row's seatId field). Pass `selected.id` to the API.
    const r = await api.holdSeat(showId, selected.id, getUserId());
    setBusy(false);
    if (r.ok) {
      navigate(`/bookings/${r.body.bookingRef}`);
    } else if (r.status === 409) {
      setErr('Seat is no longer available. Refreshing…');
      setSelected(null);
      reload();
    } else {
      setErr(`Hold failed (${r.status}).`);
    }
  }

  return (
    <div>
      <h1>Seat map</h1>
      {err && <p className="error">{err}</p>}
      {Object.keys(grouped).length === 0 && <p className="muted">Loading…</p>}
      {Object.entries(grouped).map(([row, list]) => (
        <div className="card" key={row}>
          <strong>Row {row}</strong>
          <div className="seat-map">
            {list.map((s) => {
              const isSelectable = s.status === 'AVAILABLE';
              const isSelected = selected && selected.seatId === s.seatId;
              return (
                <div
                  key={s.id}
                  className={`seat ${s.status} ${isSelected ? 'selected' : ''}`}
                  onClick={() => isSelectable && setSelected(s)}
                  title={`${row}${s.number} • ${s.status} • ${s.price}`}
                >
                  <span className="row-label">{row}</span>{s.number}
                </div>
              );
            })}
          </div>
        </div>
      ))}

      <div className="card">
        {selected ? (
          <>
            <p>
              Selected: <strong>{selected.row}{selected.number}</strong> •{' '}
              <span className="status AVAILABLE">AVAILABLE</span> •{' '}
              Price: {selected.price}
            </p>
            <button onClick={hold} disabled={busy}>
              {busy ? 'Holding…' : 'Hold seat'}
            </button>
          </>
        ) : (
          <p className="muted">Select an AVAILABLE seat to hold.</p>
        )}
      </div>
    </div>
  );
}