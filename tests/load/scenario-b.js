// Scenario B — Hold expiration and re-hold.
// Requires a short HOLD_TTL_SECONDS (e.g. 10) on the backend.
//
// Flow:
//   1. User A holds seat → expect 200 + bookingRef
//   2. Wait beyond TTL (TTL_SECONDS + small buffer)
//   3. User B holds same seat → expect 200 (because the hold expired)
//
// Run from OUTSIDE the application machine:
//   BASE_URL=https://cinemaseat.example.com \
//   SHOW_ID=101 SHOW_SEAT_ID=501 \
//   TTL_SECONDS=10 \
//   k6 run tests/load/scenario-b.js
//
// NOTE: the path variable is the show_seats.id (Agent 1's controller uses
// {showSeatId}). The SHOW_SEAT_ID env var is the canonical name; SEAT_ID
// is accepted as a back-compat alias.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHOW_ID = __ENV.SHOW_ID || '101';
const SHOW_SEAT_ID = __ENV.SHOW_SEAT_ID || __ENV.SEAT_ID || '501';
const TTL_SECONDS = Number(__ENV.TTL_SECONDS || 10);

const aSuccess = new Counter('a_hold_success');
const aFail = new Counter('a_hold_fail');
const bSuccess = new Counter('b_hold_success');
const bFail = new Counter('b_hold_fail');

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {},
};

function hold(userId) {
  const url = `${BASE_URL}/api/shows/${SHOW_ID}/seats/${SHOW_SEAT_ID}/hold`;
  return http.post(url, JSON.stringify({ userId }), {
    headers: { 'Content-Type': 'application/json' },
  });
}

export default function () {
  const start = Date.now();

  // --- User A holds ---
  const a = hold('user-A');
  const aOk = a.status === 200;
  if (aOk) aSuccess.add(1); else aFail.add(1);
  check(a, { 'A hold succeeded': (r) => r.status === 200 });

  // --- Wait beyond TTL ---
  // Sleep for the configured TTL plus a small buffer so the row's
  // hold_expires_at is strictly in the past.
  sleep(TTL_SECONDS + 2);

  // --- User B re-holds the same seat ---
  const b = hold('user-B');
  const bOk = b.status === 200;
  if (bOk) bSuccess.add(1); else bFail.add(1);
  check(b, { 'B re-hold succeeded after expiry': (r) => r.status === 200 });

  const report = {
    scenario: 'B',
    base_url: BASE_URL,
    show_id: SHOW_ID,
    seat_id: SHOW_SEAT_ID,
    ttl_seconds: TTL_SECONDS,
    elapsed_ms: Date.now() - start,
    user_a: { status: a.status, body: a.body },
    user_b: { status: b.status, body: b.body },
    expected: {
      user_a: '200 (PENDING_PAYMENT)',
      user_b: '200 after TTL (seat became available)',
    },
  };
  console.log(JSON.stringify(report, null, 2));
}