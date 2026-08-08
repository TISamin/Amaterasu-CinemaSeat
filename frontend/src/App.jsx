import { Link, Route, Routes } from 'react-router-dom';
import Movies from './pages/Movies.jsx';
import Shows from './pages/Shows.jsx';
import SeatMap from './pages/SeatMap.jsx';
import Checkout from './pages/Checkout.jsx';
import Confirmation from './pages/Confirmation.jsx';
import Health from './pages/Health.jsx';

export default function App() {
  return (
    <div>
      <header className="app-header">
        <Link to="/">🎬 CinemaSeat</Link>
        <Link to="/health" className="muted" style={{ color: '#fff' }}>health</Link>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<Movies />} />
          <Route path="/movies/:movieId/shows" element={<Shows />} />
          <Route path="/shows/:showId/seats" element={<SeatMap />} />
          <Route path="/bookings/:bookingRef" element={<Checkout />} />
          <Route path="/bookings/:bookingRef/confirmed" element={<Confirmation />} />
          <Route path="/health" element={<Health />} />
        </Routes>
      </main>
    </div>
  );
}