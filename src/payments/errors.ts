/**
 * Domain errors thrown by the payment service. Routes map these to HTTP statuses.
 * Keeping them in a single module so callers / tests can `instanceof` reliably.
 */

export class PaymentNotFoundError extends Error {
  readonly code = 'PAYMENT_NOT_FOUND';
  constructor(public readonly bookingRef: string) {
    super(`no booking found for ref: ${bookingRef}`);
    this.name = 'PaymentNotFoundError';
  }
}

/**
 * Raised when a payment has already reached a terminal state (SUCCEEDED / FAILED / REFUNDED)
 * and the caller is asking for an action that's not allowed from that state.
 */
export class PaymentTerminalError extends Error {
  readonly code = 'PAYMENT_TERMINAL';
  constructor(public readonly currentStatus: string) {
    super(`payment is in terminal state: ${currentStatus}`);
    this.name = 'PaymentTerminalError';
  }
}

/**
 * Raised when the gateway client fails to respond in time. Routes map to HTTP 504.
 */
export class PaymentGatewayTimeoutError extends Error {
  readonly code = 'GATEWAY_TIMEOUT';
  constructor(message: string) {
    super(message);
    this.name = 'PaymentGatewayTimeoutError';
  }
}

/**
 * Raised when a callback's amount/currency does not match the payment row.
 * This is a *data integrity* signal, NOT a client error: the gateway sent us a
 * callback that doesn't match what we believe the user is paying for.
 *
 * Contract decision: the HTTP layer MUST still respond 200 (the gateway would
 * otherwise retry forever and the dedup invariant wants every callback
 * acknowledged). The amount mismatch is captured in the response body so the
 * gateway / operator log can see it, and the payment state is NOT transitioned.
 *
 * Routes do NOT throw this — the service signals it via `CallbackResult.amountMismatch`.
 */
export class PaymentAmountMismatchError extends Error {
  readonly code = 'PAYMENT_AMOUNT_MISMATCH';
  constructor(
    public readonly expected: { amount: number; currency: string },
    public readonly received: { amount: number; currency: string },
  ) {
    super(
      `callback amount/currency mismatch: expected ${expected.amount} ${expected.currency}, ` +
      `got ${received.amount} ${received.currency}`,
    );
    this.name = 'PaymentAmountMismatchError';
  }
}

/**
 * Raised by /pay when the booking port cannot supply a usable amount/currency.
 * Maps to HTTP 400 — caller asked us to charge something we cannot charge.
 */
export class PaymentValidationError extends Error {
  readonly code = 'PAYMENT_VALIDATION';
  constructor(message: string) {
    super(message);
    this.name = 'PaymentValidationError';
  }
}
