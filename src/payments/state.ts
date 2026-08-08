import { CallbackStatus, PaymentRow, PaymentStatus } from './types';

/**
 * Payment state machine — single source of truth for allowed transitions.
 * Mirrors STATE_MACHINE.md §11-§13.
 */

const FORWARD: Record<PaymentStatus, ReadonlyArray<PaymentStatus>> = {
  PENDING: ['SUCCEEDED', 'FAILED'],
  SUCCEEDED: ['REFUNDED'],
  FAILED: [],
  REFUNDED: [],
};

export function canTransition(from: PaymentStatus, to: PaymentStatus): boolean {
  return FORWARD[from].includes(to);
}

export function assertTransition(from: PaymentStatus, to: PaymentStatus): void {
  if (!canTransition(from, to)) {
    throw new Error(`Illegal payment transition: ${from} -> ${to}`);
  }
}

export function paymentStatusFromCallback(s: CallbackStatus): PaymentStatus {
  switch (s) {
    case 'SUCCEEDED': return 'SUCCEEDED';
    case 'FAILED':    return 'FAILED';
    case 'REFUNDED':  return 'REFUNDED';
  }
}

/** True if the payment is in a final state. */
export function isTerminal(s: PaymentStatus): boolean {
  return s === 'SUCCEEDED' || s === 'FAILED' || s === 'REFUNDED';
}

/**
 * Update the in-memory row's status if the transition is legal.
 * Mutates and returns the row. Throws on illegal transition.
 */
export function applyTransition(row: PaymentRow, to: PaymentStatus): PaymentRow {
  assertTransition(row.status, to);
  return { ...row, status: to, updated_at: new Date() };
}
