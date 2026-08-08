import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';

export default function Movies() {
  const [movies, setMovies] = useState([]);
  const [err, setErr] = useState(null);

  useEffect(() => {
    api.getMovies().then((r) => {
      if (!r.ok) return setErr(`Failed to load movies (${r.status})`);
      setMovies(r.body || []);
    });
  }, []);

  return (
    <div>
      <h1>Movies</h1>
      {err && <p className="error">{err}</p>}
      {movies.length === 0 && !err && <p className="muted">Loading…</p>}
      <div>
        {movies.map((m) => (
          <div className="card" key={m.id}>
            <h3>{m.title}</h3>
            <p className="muted">{m.durationMinutes} min</p>
            <p>{m.description}</p>
            <Link to={`/movies/${m.id}/shows`}>
              <button>View shows</button>
            </Link>
          </div>
        ))}
      </div>
    </div>
  );
}