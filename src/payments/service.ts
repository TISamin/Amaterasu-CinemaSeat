/**
 * PaymentService is the single owner of payment state changes.
 *
 * MVP refund policy (locked):
 *   When a payment transitions SUCCEEDED -> REFUNDED, the booking stays CONFIRMED
 *   and the seat stays BOOKED. We deliberately do NOT auto-reopen the seat or move
 *   the booking back to PENDING_PAYMENT / EXPIRED. STATE_MACHINE.md §13 is explicit:
 *   "Refund behavior for the booking/seat must follow the project's explicitly
 *   implemented refund policy. Do not invent additional refund states." Our policy
 *   is "no booking/seat side-effects on refund". Any future policy change must be
 *   agreed with the team before touching `applyRefunded`.
 *
 * This file MUST be the only place that:
 *  - calls the gateway
 *  - writes to payments / payment_events
 *  - invokes the booking / show-seat ports
 */

import { PoolClient } from 'pg';
import { GatewayClient, GatewayError, GatewayTimeoutError } from '../gateway/client';
import { BookingPaymentPort } from '../ports/booking-payment.port';
import { ShowSeatPaymentPort } from '../ports/show-seat-payment.port';
import { CallbackPayload, OtpSendRequest, OtpSendResponse, OtpVerifyRequest, OtpVerifyResponse, PaymentRow } from './types';
import {
  assignGatewayPaymentId,
  createPendingPayment,
  findPaymentByIdempotencyKey,
  findPaymentByPaymentId,
  processCallbackInTx,
  updatePaymentStatus,
} from './repository';
import { applyTransition, isTerminal, paymentStatusFromCallback } from './state';
import {
  PaymentNotFoundError,
  PaymentTerminalError,
  PaymentValidationError,
} from './errors';

export interface PayOptions {
  /** Override the gateway's X-Mock-Force mode. Used by tests. */
  mockForce?: string;
  /** The absolute callback URL the gateway should hit. */
  callbackUrl: string;
}

export interface PayResult {
  paymentId: string;
  bookingRef: string;
  status: PaymentRow['status'];
  /** True if this call reused an existing pending payment (idempotent retry). */
  reused: boolean;
}

export interface CallbackResult {
  duplicate: boolean;
  paymentId: string;
  bookingRef: string;
  finalStatus: PaymentRow['status'] | null;
  /**
   * True when the callback's amount/currency didn't match the payment row.
   * State is NOT transitioned in this case; the gateway still receives HTTP 200.
   * Caller MUST log this — it indicates either a misconfigured gateway or
   * a tampering attempt.
   */
  amountMismatch: boolean;
}

// Error classes live in ./errors.ts.

export class PaymentService {
  constructor(
    public readonly gateway: GatewayClient,
    private readonly bookings: BookingPaymentPort,
    private readonly seats: ShowSeatPaymentPort,
  ) {}

  /**
   * Build a stable idempotency key for a (booking_ref, attempt) pair.
   * Attempts are bounded — we always use attempt 1 unless explicitly told otherwise.
   * Future retried `/pay` calls pass the same booking_ref, so they reuse this key.
   */
  static idempotencyKeyFor(bookingRef: string, attempt = 1): string {
    return `pay:${bookingRef}:${attempt}`;
  }

  /**
   * /pay handler. Synchronous to the caller:
   *   1. validate booking (port)
   *   2. obtain booking amount/currency from the booking port (REFUND source of truth)
   *   3. ensure a PENDING payment row exists (tx, committed)
   *   4. call gateway /charge (NO tx open)
   *   5. assign gateway payment_id (tx, committed)
   *   6. return 202
   *
   * The /charge HTTP call is NEVER inside a DB transaction.
   */
  async pay(bookingRef: string, opts: PayOptions): Promise<PayResult> {
    // 1. validate booking
    const bkStatus = await this.bookings.getStatus(bookingRef);
    if (bkStatus === null) {
      throw new PaymentNotFoundError(bookingRef);
    }
    if (bkStatus !== 'PENDING_PAYMENT') {
      throw new PaymentTerminalError(bkStatus);
    }

    // 2. obtain booking amount/currency — the booking port is the source of truth.
    const price = await this.bookings.getAmountByRef(bookingRef);
    if (!price || !Number.isFinite(price.amount) || price.amount <= 0 || !price.currency) {
      throw new PaymentValidationError(
        `booking ${bookingRef} has no usable price (got ${JSON.stringify(price)})`,
      );
    }

    // 3. ensure PENDING payment row
    const idemKey = PaymentService.idempotencyKeyFor(bookingRef);
    let payment = await findPaymentByIdempotencyKey(idemKey);
    let reused = false;
    if (payment && isTerminal(payment.status)) {
      throw new PaymentTerminalError(payment.status);
    }
    if (!payment) {
      payment = await createPendingPayment({
        bookingRef,
        amount: price.amount,
        currency: price.currency,
        idempotencyKey: idemKey,
      });
    } else {
      // Existing pending row: assert its amount/currency matches the booking's current price.
      // If they differ, the booking was changed underneath us — refuse to charge.
      if (payment.amount !== price.amount || payment.currency !== price.currency) {
        throw new PaymentValidationError(
          `existing pending payment amount/currency (${payment.amount} ${payment.currency}) ` +
          `does not match booking ${bookingRef} (${price.amount} ${price.currency})`,
        );
      }
      reused = true;
    }

    // 4. call gateway (NO tx open)
    let charge;
    try {
      charge = await this.gateway.charge(
        {
          amount: payment.amount,
          currency: payment.currency,
          booking_ref: bookingRef,
          callback_url: opts.callbackUrl,
        },
        idemKey,
        opts.mockForce,
      );
    } catch (err) {
      // Gateway failure surfaces cleanly; do not corrupt state.
      // If timeout / 5xx, the PENDING row remains. /charge can be retried with the same idempotency key.
      if (err instanceof GatewayError || err instanceof GatewayTimeoutError) throw err;
      throw err;
    }

    // 5. assign gateway payment_id
    await assignGatewayPaymentId(payment.payment_id, charge.payment_id);

    return {
      paymentId: charge.payment_id,
      bookingRef,
      status: 'PENDING',
      reused,
    };
  }

  /**
   * Callback handler. Safe under concurrency:
   *  - duplicate event_id → UNIQUE violation → return 200 with no state change
   *  - callback before /pay persistence → look up by gateway payment_id and create a
   *    minimal pending row if missing (with callback's amount as a temporary placeholder;
   *    the booking port's amount is the source of truth, so we re-validate on the
   *    *next* /pay call against this row.)
   *  - amount/currency mismatch → event is recorded, but state is NOT transitioned;
   *    `CallbackResult.amountMismatch = true` so the route can return 200 + signal it.
   *  - all state transitions inside a single tx
   */
  async handleCallback(payload: CallbackPayload): Promise<CallbackResult> {
    const event = {
      event_id: payload.event_id,
      payment_id: payload.payment_id,
      booking_ref: payload.booking_ref,
      status: payload.status,
      amount: payload.amount,
      currency: payload.currency,
    };

    let amountMismatch = false;

    const outcome = await processCallbackInTx(event, async (client) => {
      const targetStatus = paymentStatusFromCallback(payload.status);
      let payment = await findPaymentByPaymentId(payload.payment_id);

      // Race: callback arrived before /pay committed. Create a minimal payment row.
      // The amount/currency from the callback is recorded here ONLY as a placeholder;
      // /pay will re-validate against the booking port before charging.
      if (!payment) {
        const idemKey = PaymentService.idempotencyKeyFor(payload.booking_ref);
        payment = await createPendingPayment({
          bookingRef: payload.booking_ref,
          amount: payload.amount,
          currency: payload.currency,
          idempotencyKey: idemKey,
          paymentId: payload.payment_id,
        });
      }

      // Amount/currency invariant: the payment row's amount/currency is the source
      // of truth (set by /pay from the booking port). If the callback disagrees,
      // do NOT transition state. The event is still recorded (the gateway gets 200
      // and won't retry), but the payment stays in its current state.
      if (payment.amount !== payload.amount || payment.currency !== payload.currency) {
        amountMismatch = true;
        return;
      }

      // Already in target state → nothing to do (idempotent on the payment table).
      if (payment.status === targetStatus) {
        return;
      }

      // Apply transition + drive booking / seat ports.
      if (targetStatus === 'SUCCEEDED') {
        await this.applySuccess(client, payment);
      } else if (targetStatus === 'FAILED') {
        await this.applyFailed(client, payment);
      } else if (targetStatus === 'REFUNDED') {
        await this.applyRefunded(client, payment);
      }
    });

    if (outcome.kind === 'duplicate') {
      const existing = await findPaymentByPaymentId(payload.payment_id);
      return {
        duplicate: true,
        paymentId: payload.payment_id,
        bookingRef: payload.booking_ref,
        finalStatus: existing?.status ?? null,
        amountMismatch: false,
      };
    }

    const final = await findPaymentByPaymentId(payload.payment_id);
    return {
      duplicate: false,
      paymentId: payload.payment_id,
      bookingRef: payload.booking_ref,
      finalStatus: final?.status ?? null,
      amountMismatch,
    };
  }

  /**
   * Forward OTP send to gateway. Used by the OTP route layer.
   */
  async otpSend(req: OtpSendRequest): Promise<OtpSendResponse> {
    return this.gateway.otpSend(req);
  }

  async otpVerify(req: OtpVerifyRequest): Promise<OtpVerifyResponse> {
    return this.gateway.otpVerify(req);
  }

  private async applySuccess(client: PoolClient, payment: PaymentRow): Promise<void> {
    applyTransition(payment, 'SUCCEEDED');
    await updatePaymentStatus(payment.payment_id, 'SUCCEEDED', client);
    await this.bookings.confirmByRef(payment.booking_ref);
    await this.seats.bookForBooking(payment.booking_ref);
  }

  private async applyFailed(client: PoolClient, payment: PaymentRow): Promise<void> {
    applyTransition(payment, 'FAILED');
    await updatePaymentStatus(payment.payment_id, 'FAILED', client);
    await this.bookings.markPaymentFailed(payment.booking_ref);
    await this.seats.releaseForBooking(payment.booking_ref);
  }

  /**
   * MVP refund policy:
   *   Update only `payments.status = REFUNDED`. Do NOT touch bookings or show_seats.
   *   The booking stays CONFIRMED and the seat stays BOOKED. See the file header.
   *   If a future policy is agreed (e.g. "refund releases the seat back to AVAILABLE"),
   *   implement it here AND update the test in `payment-service.test.ts`.
   */
  private async applyRefunded(client: PoolClient, payment: PaymentRow): Promise<void> {
    applyTransition(payment, 'REFUNDED');
    await updatePaymentStatus(payment.payment_id, 'REFUNDED', client);
    // Intentionally no booking port call. Intentionally no seat port call.
  }

  /**
   * Refund initiation. The gateway POST /refund is async; the workflow completes
   * when the gateway sends a REFUNDED callback. Until then the payment stays SUCCEEDED.
   */
  async refund(paymentId: string, opts: PayOptions): Promise<{ refundId: string; status: 'PENDING' | 'REFUNDED' }> {
    const payment = await findPaymentByPaymentId(paymentId);
    if (!payment) throw new PaymentNotFoundError(paymentId);
    if (payment.status !== 'SUCCEEDED') throw new PaymentTerminalError(payment.status);
    const r = await this.gateway.refund({ payment_id: paymentId }, opts.mockForce);
    return { refundId: r.refund_id, status: r.status };
  }
}