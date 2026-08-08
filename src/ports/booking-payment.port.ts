/**
 * Port: BookingPaymentPort
 *
 * Agent 2 uses this port to move the booking through its payment-driven transitions.
 * The state machine is documented in STATE_MACHINE.md §7-§10.
 *
 * Agent 1 ships a concrete implementation. Agent 2 ships a NoopBookingAdapter
 * so the suite runs standalone pre-merge.
 */

export type BookingStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'PAYMENT_FAILED' | 'EXPIRED';

export interface BookingAmount {
  /** Integer in the smallest currency unit (e.g. cents/poisha). Must be > 0. */
  amount: number;
  /** ISO 4217 currency code, e.g. "BDT". */
  currency: string;
}

export interface BookingPaymentPort {
  /**
   * Mark the booking as CONFIRMED.
   * - Must be idempotent: CONFIRMED -> CONFIRMED is a no-op.
   * - Must not throw if the booking is already CONFIRMED.
   * - Must throw if the booking is in a state from which CONFIRMED is not allowed
   *   (e.g. EXPIRED), so the caller can decide whether to rollback.
   */
  confirmByRef(bookingRef: string): Promise<void>;

  /**
   * Mark the booking as PAYMENT_FAILED.
   * - Idempotent: PAYMENT_FAILED -> PAYMENT_FAILED is a no-op.
   */
  markPaymentFailed(bookingRef: string): Promise<void>;

  /**
   * Read the booking's current status. Used by the /pay endpoint for pre-charge validation.
   * Returns null if the booking does not exist.
   */
  getStatus(bookingRef: string): Promise<BookingStatus | null>;

  /**
   * Read the booking's payable amount/currency. This is the AUTHORITATIVE source of
   * truth for what the gateway should charge — Agent 2 never decides the price.
   *
   * Contract:
   *  - Return null if the booking does not exist or has no usable price.
   *  - `amount` MUST be a positive integer in the smallest currency unit.
   *  - `currency` MUST be a non-empty ISO 4217 code.
   *  - Agent 1's adapter reads this from the booking/seat/show entity.
   *  - The Noop adapter returns a configured value (default 450 BDT).
   */
  getAmountByRef(bookingRef: string): Promise<BookingAmount | null>;
}

/**
 * Default adapter used until Agent 1 ships. Records calls for assertions.
 * Configurable behaviors:
 *  - `failLookup: true` makes `getStatus()` return null (simulates a missing booking).
 *  - `defaultAmount: { amount, currency }` controls what `getAmountByRef()` returns.
 *    Default: `{ amount: 450, currency: 'BDT' }`. Pass `{ amount: 0, currency: 'BDT' }`
 *    (or null) to simulate a booking that has no usable price.
 */
export class NoopBookingAdapter implements BookingPaymentPort {
  public readonly calls: Array<{ method: string; bookingRef: string }> = [];

  constructor(
    private readonly opts: {
      failLookup?: boolean;
      /** When set, overrides the default 450 BDT. Use `null` to simulate "no price". */
      defaultAmount?: BookingAmount | null;
    } = {},
  ) {
    // Normalise: if the caller did not set defaultAmount at all, fall back to the
    // historical 450 BDT default. If they explicitly set it (including to null), honor it.
    if (!('defaultAmount' in opts)) {
      this.opts = { ...opts, defaultAmount: { amount: 450, currency: 'BDT' } };
    }
  }

  async confirmByRef(bookingRef: string): Promise<void> {
    this.calls.push({ method: 'confirmByRef', bookingRef });
  }

  async markPaymentFailed(bookingRef: string): Promise<void> {
    this.calls.push({ method: 'markPaymentFailed', bookingRef });
  }

  async getStatus(bookingRef: string): Promise<BookingStatus | null> {
    this.calls.push({ method: 'getStatus', bookingRef });
    if (this.opts.failLookup) return null;
    // Pretend every booking is in PENDING_PAYMENT, so the /pay endpoint can proceed.
    return 'PENDING_PAYMENT';
  }

  async getAmountByRef(bookingRef: string): Promise<BookingAmount | null> {
    this.calls.push({ method: 'getAmountByRef', bookingRef });
    if (this.opts.failLookup) return null;
    return this.opts.defaultAmount ?? null;
  }
}
