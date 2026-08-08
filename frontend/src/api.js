// Thin API client. Endpoints and shapes are pinned to docs/API_CONTRACT.md.
// Base URL is injected at build time:
//   - Docker build: /api  -> nginx in this same image reverse-proxies to api:3000
//   - Local dev:     http://localhost:3000 (Vite proxy handles /api in dev too)

const BASE = (typeof __API_BASE_URL__ !== 'undefined' && __API_BASE_URL__) || '/api';

async function jsonFetch(url, options = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });

  // Try to parse JSON regardless of status; some endpoints (e.g. 409) return
  // a structured error body that we want to surface.
  let body = null;
  try {
    body = await res.json();
  } catch (_) {
    body = null;
  }
  return { ok: res.ok, status: res.status, body };
}

export const api = {
  base: BASE,
  url(path) {
    return BASE.replace(/\/$/, '') + path;
  },

  getMovies() {
    return jsonFetch(this.url('/api/movies'));
  },

  getMovieShows(movieId) {
    return jsonFetch(this.url(`/api/movies/${movieId}/shows`));
  },

  getSeatMap(showId) {
    return jsonFetch(this.url(`/api/shows/${showId}/seats`));
  },

  holdSeat(showId, seatId, userId) {
    return jsonFetch(this.url(`/api/shows/${showId}/seats/${seatId}/hold`), {
      method: 'POST',
      body: JSON.stringify({ userId }),
    });
  },

  getBooking(bookingRef) {
    return jsonFetch(this.url(`/api/bookings/${bookingRef}`));
  },

  pay(bookingRef) {
    return jsonFetch(this.url(`/api/bookings/${bookingRef}/pay`), {
      method: 'POST',
    });
  },

  getHealth() {
    return jsonFetch(this.url('/health'));
  },
};