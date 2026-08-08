/**
 * Integration test: duplicate-callback handling against real PostgreSQL.
 *
 * Audit item H-3 (REQUIRED before staging): prove that the `payment_events.event_id`
 * UNIQUE constraint actually short-circuits duplicate callbacks, without relying
 * on the mocked repository used in unit tests.
 *
 * Strategy:
 *   1. Connect to the database at INTEGRATION_DATABASE_URL.
 *   2. Create an isolated schema for this test run (avoids stomping on dev data).
 *   3. Apply the two migrations against the schema.
 *   4. Wire a real-Postgres-backed PaymentService with a fake gateway.
 *   5. Pre-insert a pending payment row (skips the /pay -> gateway call).
 *   6. Send the SAME callback twice via the Fastify HTTP layer.
 *   7. Assert:
 *        - both HTTP responses are 200
 *        - exactly one row in payment_events for that event_id
 *        - exactly one status transition PENDING -> SUCCEEDED
 *        - confirmByRef was called exactly once
 *
 * Skip the test when INTEGRATION_DATABASE_URL is not set.
 *   Run with: $env:INTEGRATION_DATABASE_URL="postgres://user:pass@host:5432/db"; npm test
 */

import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
import { Pool, PoolClient } from 'pg';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { buildApp } from '../../src/app';
import { setPool } from '../../src/db/pool';
import { NoopBookingAdapter } from '../../src/ports/booking-payment.port';
import { NoopShowSeatAdapter } from '../../src/ports/show-seat-payment.port';
import { GatewayClient } from '../../src/gateway/client';

const INT_DB_URL = process.env.INTEGRATION_DATABASE_URL;
const describeIf = INT_DB_URL ? describe : describe.skip;

describeIf('INTEGRATION: payment callback deduplication (H-3, real PostgreSQL)', () => {
  let pool: Pool;
  let schemaName: string;
  let app: ReturnType<typeof buildApp>;
  let bookings: NoopBookingAdapter;

  // ---------- helpers ----------

  /** Apply the two migrations against the chosen schema. */
  async function applyMigrations(c: PoolClient): Promise<void> {
    const mig1 = readFileSync(resolve(__dirname, '../../migrations/1700000000000_payments.sql'), 'utf8');
    const mig2 = readFileSync(resolve(__dirname, '../../migrations/1700000000001_payment_events.sql'), 'utf8');
    await c.query(`SET search_path TO "${schemaName}"`);
    // Migrations have a "-- Down Migration" separator; we only want the up parts.
    const up1 = mig1.split('-- Down Migration')[0]!;
    const up2 = mig2.split('-- Down Migration')[0]!;
    await c.query(up1);
    await c.query(up2);
  }

  /** Drop the isolated schema when we're done. */
  async function dropSchema(c: PoolClient): Promise<void> {
    await c.query(`DROP SCHEMA IF EXISTS "${schemaName}" CASCADE`);
  }

  // ---------- lifecycle ----------

  beforeAll(async () => {
    pool = new Pool({ connectionString: INT_DB_URL });

    // Use a per-run schema name so parallel CI runs do not collide.
    schemaName = `payment_dedup_${Date.now()}_${Math.floor(Math.random() * 1e6)}`;
    const setupClient = await pool.connect();
    try {
      await setupClient.query(`CREATE SCHEMA "${schemaName}"`);
      await applyMigrations(setupClient);
    } finally {
      setupClient.release();
    }

    // The repository reads from the default connection; force the search_path there.
    // We rely on the convention: every query in repository.ts uses unqualified
    // table names, and we set search_path per-transaction / per-connection via
    // the connect handler below.
    pool.on('connect', (client) => {
      client.query(`SET search_path TO "${schemaName}"`).catch(() => { /* ignore */ });
    });

    // Re-issue a connection so the search_path is set on at least one connection
    // before any repository query runs.
    {
      const warm = await pool.connect();
      try { await warm.query('SELECT 1'); } finally { warm.release(); }
    }

    setPool(pool);

    // Build the app with a fake gateway so we can isolate the dedup logic.
    const gateway = {
      charge: vi.fn().mockResolvedValue({ payment_id: 'pi-int-1', status: 'PENDING' }),
      refund: vi.fn().mockResolvedValue({ refund_id: 'rf-1', status: 'REFUNDED' }),
      otpSend: vi.fn(),
      otpVerify: vi.fn(),
    } as unknown as GatewayClient;
    bookings = new NoopBookingAdapter({ defaultAmount: { amount: 450, currency: 'BDT' } });
    const seats = new NoopShowSeatAdapter();

    app = buildApp({
      cfg: {
        apiHost: '127.0.0.1',
        apiPort: 0,
        backendServiceName: 'api',
        databaseUrl: INT_DB_URL!,
        gatewayBaseUrl: 'http://gw:9000',
        gatewayPathPrefix: '',
        gatewayTimeoutMs: 1000,
        gatewaySecret: undefined,
        hmacEnabled: false,
        signatureEncoding: 'hex',
      },
      gateway,
      bookings,
      seats,
    });
  }, 30000);

  afterAll(async () => {
    try {
      if (app) await app.close();
    } catch { /* ignore */ }
    try {
      if (pool) {
        const c = await pool.connect();
        try { await dropSchema(c); } finally { c.release(); }
        await pool.end();
      }
    } catch { /* ignore */ }
    // Note: we do NOT call closePool() here. closePool() would call pool.end() a
    // second time on the SAME Pool instance we already ended, which throws
    // "Called end on pool more than once". The setPool(pool) reference is now
    // stale; leave it — vitest is about to exit anyway. If you ever reuse this
    // file in a long-running process, also reset the module's _pool ref.
  });

  // ---------- the test ----------

  it('processes a callback once and dedups all subsequent retries with the same event_id', async () => {
    // 1. Pre-insert a PENDING payment row keyed by a known payment_id.
    //    We do NOT call /pay (that would need a gateway), so the row is created directly.
    const gatewayPaymentId = 'pi-int-dedup';
    const bookingRef = 'BR-INT-1';
    await pool.query(
      `INSERT INTO payments (payment_id, booking_ref, status, amount, currency, idempotency_key)
       VALUES ($1, $2, 'PENDING', 450, 'BDT', $3)`,
      [gatewayPaymentId, bookingRef, `pay:${bookingRef}:1`],
    );

    // 2. Build the callback body and sign it. HMAC is disabled in cfg, so we don't need to sign.
    const callbackBody = {
      event_id: 'evt-int-dedup-1',
      payment_id: gatewayPaymentId,
      status: 'SUCCEEDED',
      booking_ref: bookingRef,
      amount: 450,
      currency: 'BDT',
      timestamp: '2024-01-01T00:00:00Z',
    };

    // 3. Fire the SAME callback twice (the gateway often retries the same event_id).
    const res1 = await app.inject({
      method: 'POST',
      url: '/api/payments/callback',
      payload: callbackBody,
    });
    const res2 = await app.inject({
      method: 'POST',
      url: '/api/payments/callback',
      payload: callbackBody,
    });

    // 4. Both HTTP responses must be 200.
    expect(res1.statusCode).toBe(200);
    expect(res2.statusCode).toBe(200);

    const body1 = res1.json();
    const body2 = res2.json();
    // First response: state transitioned. Second response: duplicate, NO state change.
    // The route exposes the resulting payment status under `status` (see routes.ts).
    expect(body1.status).toBe('SUCCEEDED');
    expect(body1.duplicate).toBe(false);
    expect(body2.status).toBe('SUCCEEDED');
    expect(body2.duplicate).toBe(true);

    // 5. Exactly ONE row in payment_events for our event_id.
    const evRes = await pool.query(
      `SELECT * FROM payment_events WHERE event_id = $1`,
      ['evt-int-dedup-1'],
    );
    expect(evRes.rows).toHaveLength(1);

    // 6. The payment row is SUCCEEDED (not stuck in PENDING, not re-transitioned).
    const payRes = await pool.query(
      `SELECT * FROM payments WHERE payment_id = $1`,
      [gatewayPaymentId],
    );
    expect(payRes.rows).toHaveLength(1);
    expect(payRes.rows[0].status).toBe('SUCCEEDED');

    // 7. confirmByRef was called EXACTLY ONCE (the dedup short-circuits port calls).
    const confirmCalls = bookings.calls.filter((c) => c.method === 'confirmByRef');
    expect(confirmCalls).toHaveLength(1);
  }, 15000);

  it('records amountMismatch but does NOT transition state when amounts differ', async () => {
    const gatewayPaymentId = 'pi-int-mismatch';
    const bookingRef = 'BR-INT-2';
    await pool.query(
      `INSERT INTO payments (payment_id, booking_ref, status, amount, currency, idempotency_key)
       VALUES ($1, $2, 'PENDING', 450, 'BDT', $3)`,
      [gatewayPaymentId, bookingRef, `pay:${bookingRef}:1`],
    );

    // Different amount: 1000 instead of 450.
    const res = await app.inject({
      method: 'POST',
      url: '/api/payments/callback',
      payload: {
        event_id: 'evt-int-mismatch-1',
        payment_id: gatewayPaymentId,
        status: 'SUCCEEDED',
        booking_ref: bookingRef,
        amount: 1000,
        currency: 'BDT',
        timestamp: '2024-01-01T00:00:00Z',
      },
    });

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.amountMismatch).toBe(true);
    expect(body.status).toBe('PENDING'); // unchanged

    // The event was still recorded (audit trail), but state did not move.
    const evRes = await pool.query(
      `SELECT * FROM payment_events WHERE event_id = $1`,
      ['evt-int-mismatch-1'],
    );
    expect(evRes.rows).toHaveLength(1);

    const payRes = await pool.query(
      `SELECT status FROM payments WHERE payment_id = $1`,
      [gatewayPaymentId],
    );
    expect(payRes.rows[0].status).toBe('PENDING');
  }, 15000);
});
