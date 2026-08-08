import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';

export default function Shows() {
  const { movieId } = useParams();
  const [shows, setShows] = useState([]);
  const [err, setErr] = useState(null);

  useEffect(() => {
    api.getMovieShows(movieId).then((r) => {
      if (!r.ok) return setErr(`Failed to load shows (${r.status})`);
      setShows(r.body || []);
    });
  }, [movieId]);

  return (
    <div>
      <h1>Showtimes</h1>
      {err && <p className="error">{err}</p>}
      {shows.length === 0 && !err && <p className="muted">Loading…</p>}
      {shows.map((s) => (
        <div className="card" key={s.id}>
          <h3>{s.theatreName} — {s.screenName}</h3>
          <p>{new Date(s.startTime).toLocaleString()}</p>
          <p>Price: {s.price}</p>
          <Link to={`/shows/${s.id}/seats`}>
            <button>Select seats</button>
          </Link>
        </div>
      ))}
    </div>
  );
}