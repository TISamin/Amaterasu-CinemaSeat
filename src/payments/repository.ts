import { PoolClient } from 'pg';
import { getPool, withTx } from '../db/pool';
import { PaymentEventRow, PaymentRow } from './types';

/**
 * Repository for the `payments` table. All queries are parameterized; no string concat.
 */

export async function findPaymentByIdempotencyKey(key: string): Promise<PaymentRow | null> {
  const r = await getPool().query<PaymentRow>(
    `SELECT * FROM payments WHERE idempotency_key = $1`,
    [key],
  );
  return r.rows[0] ?? null;
}

export async function findPaymentByPaymentId(paymentId: string): Promise<PaymentRow | null> {
  const r = await getPool().query<PaymentRow>(
    `SELECT * FROM payments WHERE payment_id = $1`,
    [paymentId],
  );
  return r.rows[0] ?? null;
}

export async function findPaymentByBookingRef(bookingRef: string): Promise<PaymentRow | null> {
  const r = await getPool().query<PaymentRow>(
    `SELECT * FROM payments WHERE booking_ref = $1 ORDER BY id DESC LIMIT 1`,
    [bookingRef],
  );
  return r.rows[0] ?? null;
}

export interface CreatePendingPaymentInput {
  bookingRef: string;
  amount: number;
  currency: string;
  idempotencyKey: string;
  /** Optional: when known (e.g. late callback / race), the gateway's payment_id. */
  paymentId?: string;
}

export async function createPendingPayment(input: CreatePendingPaymentInput): Promise<PaymentRow> {
  const r = await getPool().query<PaymentRow>(
    `INSERT INTO payments (payment_id, booking_ref, status, amount, currency, idempotency_key)
     VALUES ($1, $2, 'PENDING', $3, $4, $5)
     RETURNING *`,
    [
      input.paymentId ?? `pending-${input.idempotencyKey}`,
      input.bookingRef,
      input.amount,
      input.currency,
      input.idempotencyKey,
    ],
  );
  return r.rows[0]!;
}

/**
 * Replaces the placeholder payment_id with the gateway's authoritative one.
 * Idempotent: if the row already has the target payment_id, no-op.
 * Uses an UPDATE … WHERE … to avoid check-then-act races.
 */
export async function assignGatewayPaymentId(
  currentPlaceholderId: string,
  gatewayPaymentId: string,
): Promise<PaymentRow | null> {
  const r = await getPool().query<PaymentRow>(
    `UPDATE payments
        SET payment_id = $1,
            updated_at = now()
      WHERE payment_id = $2
        AND payment_id <> $1
      RETURNING *`,
    [gatewayPaymentId, currentPlaceholderId],
  );
  return r.rows[0] ?? null;
}

export async function updatePaymentStatus(
  paymentId: string,
  status: PaymentRow['status'],
  client?: PoolClient,
): Promise<PaymentRow | null> {
  const runner = client ?? getPool();
  const r = await runner.query<PaymentRow>(
    `UPDATE payments
        SET status = $1,
            updated_at = now()
      WHERE payment_id = $2
      RETURNING *`,
    [status, paymentId],
  );
  return r.rows[0] ?? null;
}

/**
 * Records a callback event. Throws on duplicate event_id (UNIQUE violation).
 * Caller catches the PG error code `23505` to short-circuit as duplicate.
 */
export async function recordPaymentEvent(
  event: Omit<PaymentEventRow, 'id' | 'received_at' | 'processed_at'>,
  client?: PoolClient,
): Promise<PaymentEventRow> {
  const runner = client ?? getPool();
  const r = await runner.query<PaymentEventRow>(
    `INSERT INTO payment_events (event_id, payment_id, booking_ref, status, amount, currency, received_at)
     VALUES ($1, $2, $3, $4, $5, $6, now())
     RETURNING *`,
    [
      event.event_id,
      event.payment_id,
      event.booking_ref,
      event.status,
      event.amount,
      event.currency,
    ],
  );
  return r.rows[0]!;
}

export async function findPaymentEvent(eventId: string): Promise<PaymentEventRow | null> {
  const r = await getPool().query<PaymentEventRow>(
    `SELECT * FROM payment_events WHERE event_id = $1`,
    [eventId],
  );
  return r.rows[0] ?? null;
}

export async function findPaymentEventForUpdate(
  eventId: string,
  client: PoolClient,
): Promise<PaymentEventRow | null> {
  const r = await client.query<PaymentEventRow>(
    `SELECT * FROM payment_events WHERE event_id = $1 FOR UPDATE`,
    [eventId],
  );
  return r.rows[0] ?? null;
}

export async function markPaymentEventProcessed(
  eventId: string,
  client: PoolClient,
): Promise<void> {
  await client.query(
    `UPDATE payment_events SET processed_at = now() WHERE event_id = $1`,
    [eventId],
  );
}

/**
 * Convenience: do the whole "record + update payment + ports" sequence in one tx.
 * The caller passes a closure that performs the booking/seat port calls.
 * If the event_id already exists, returns 'duplicate' WITHOUT touching ports.
 */
export type CallbackTxOutcome =
  | { kind: 'processed'; event: PaymentEventRow }
  | { kind: 'duplicate' };

export async function processCallbackInTx(
  event: Omit<PaymentEventRow, 'id' | 'received_at' | 'processed_at'>,
  applyState: (client: PoolClient) => Promise<void>,
): Promise<CallbackTxOutcome> {
  return withTx(async (client) => {
    // 1. Try to insert event row. UNIQUE(event_id) protects against duplicate.
    let insertRes;
    try {
      insertRes = await client.query<PaymentEventRow>(
        `INSERT INTO payment_events (event_id, payment_id, booking_ref, status, amount, currency, received_at)
         VALUES ($1, $2, $3, $4, $5, $6, now())
         RETURNING *`,
        [event.event_id, event.payment_id, event.booking_ref, event.status, event.amount, event.currency],
      );
    } catch (err: unknown) {
      if (isUniqueViolation(err)) {
        return { kind: 'duplicate' } as const;
      }
      throw err;
    }
    const eventRow = insertRes.rows[0]!;
    // 2. Apply state transitions (caller-provided closure).
    await applyState(client);
    // 3. Mark event processed.
    await client.query(
      `UPDATE payment_events SET processed_at = now() WHERE id = $1`,
      [eventRow.id],
    );
    return { kind: 'processed', event: eventRow } as const;
  });
}

function isUniqueViolation(err: unknown): boolean {
  return !!err && typeof err === 'object' && (err as { code?: string }).code === '23505';
}
