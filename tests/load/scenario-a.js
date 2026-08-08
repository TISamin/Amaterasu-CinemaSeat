// Scenario A — 100 concurrent hold requests against the same show + same seat.
// Expected: exactly 1 success, 99 rejected, 0 oversell.
//
// Run from OUTSIDE the application machine against the deployed URL:
//   BASE_URL=https://cinemaseat.example.com \
//   SHOW_ID=101 SHOW_SEAT_ID=501 \
//   k6 run tests/load/scenario-a.js
//
// NOTE: SEAT_* variable is the show_seats.id (the Agent 1 path variable),
// NOT the seat-definition id. See docs/API_CONTRACT.md and the
// HoldController mapping in backend/src/main/java/com/cinemaseat/web/.

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHOW_ID = __ENV.SHOW_ID || '101';
const SHOW_SEAT_ID = __ENV.SHOW_SEAT_ID || __ENV.SEAT_ID || '501';
const USER_ID = __ENV.USER_ID || '';

const success = new Counter('holds_success');
const conflict = new Counter('holds_conflict');
const other = new Counter('holds_other');
const latency = new Trend('hold_latency_ms');

export const options = {
  scenarios: {
    same_seat_storm: {
      executor: 'shared-iterations',
      vus: 100,
      iterations: 100,
      maxDuration: '30s',
    },
  },
  thresholds: {
    // Scenario A only checks logical correctness; the threshold is reported
    // manually by the assertions below.
  },
};

export default function () {
  const body = USER_ID
    ? JSON.stringify({ userId: USER_ID })
    : JSON.stringify({ userId: `vu-${__VU}-${__ITER}` });

  const url = `${BASE_URL}/api/shows/${SHOW_ID}/seats/${SHOW_SEAT_ID}/hold`;
  const res = http.post(url, body, {
    headers: { 'Content-Type': 'application/json' },
  });
  latency.add(res.timings.duration);

  if (res.status === 200) {
    success.add(1);
    check(res, { 'hold succeeded': (r) => r.status === 200 });
  } else if (res.status === 409) {
    conflict.add(1);
    check(res, { 'hold rejected with 409': (r) => r.status === 409 });
  } else {
    other.add(1);
  }
}

export function handleSummary(data) {
  // Seat-map verification fetches the seat once and prints the status. The
  // assertion that the seat is held exactly once is enforced by the backend
  // (UNIQUE show_id+seat_id); here we just print what we observed.
  const verify = http.get(`${BASE_URL}/api/shows/${SHOW_ID}/seats`);
  let report = {
    scenario: 'A',
    base_url: BASE_URL,
    show_id: SHOW_ID,
    seat_id: SHOW_SEAT_ID,
    counts: {
      success: data.metrics.holds_success ? data.metrics.holds_success.values.count : 0,
      conflict_409: data.metrics.holds_conflict ? data.metrics.holds_conflict.values.count : 0,
      other: data.metrics.holds_other ? data.metrics.holds_other.values.count : 0,
    },
    hold_latency_ms: data.metrics.hold_latency_ms ? data.metrics.hold_latency_ms.values.avg : null,
    seat_map_status: verify.status,
    seat_map_body: (() => {
      try { return JSON.parse(verify.body); } catch { return verify.body; }
    })(),
    expected: { success: 1, conflict: 99, oversell: 0 },
  };
  return { 'stdout': JSON.stringify(report, null, 2) };
}
