import { describe, it, expect } from 'vitest';
import { canTransition, isTerminal, paymentStatusFromCallback } from '../src/payments/state';

describe('payment state machine', () => {
  it('PENDING -> SUCCEEDED is allowed', () => {
    expect(canTransition('PENDING', 'SUCCEEDED')).toBe(true);
  });
  it('PENDING -> FAILED is allowed', () => {
    expect(canTransition('PENDING', 'FAILED')).toBe(true);
  });
  it('SUCCEEDED -> REFUNDED is allowed', () => {
    expect(canTransition('SUCCEEDED', 'REFUNDED')).toBe(true);
  });
  it('PENDING -> REFUNDED is forbidden', () => {
    expect(canTransition('PENDING', 'REFUNDED')).toBe(false);
  });
  it('FAILED -> REFUNDED is forbidden', () => {
    expect(canTransition('FAILED', 'REFUNDED')).toBe(false);
  });
  it('SUCCEEDED -> FAILED is forbidden', () => {
    expect(canTransition('SUCCEEDED', 'FAILED')).toBe(false);
  });
  it('isTerminal: SUCCEEDED/FAILED/REFUNDED are terminal; PENDING is not', () => {
    expect(isTerminal('SUCCEEDED')).toBe(true);
    expect(isTerminal('FAILED')).toBe(true);
    expect(isTerminal('REFUNDED')).toBe(true);
    expect(isTerminal('PENDING')).toBe(false);
  });
  it('paymentStatusFromCallback maps correctly', () => {
    expect(paymentStatusFromCallback('SUCCEEDED')).toBe('SUCCEEDED');
    expect(paymentStatusFromCallback('FAILED')).toBe('FAILED');
    expect(paymentStatusFromCallback('REFUNDED')).toBe('REFUNDED');
  });
});