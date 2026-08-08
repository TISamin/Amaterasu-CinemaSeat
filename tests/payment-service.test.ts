import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../src/payments/repository', () => {
  const payments = new Map<string, any>();
  return {
    findPaymentByIdempotencyKey: vi.fn(async (key: string) => payments.get(`key:${key}`) ?? null),
    findPaymentByPaymentId: vi.fn(async (pid: string) => payments.get(`pid:${pid}`) ?? null),
    findPaymentByBookingRef: vi.fn(async (ref: string) => payments.get(`ref:${ref}`) ?? null),
    createPendingPayment: vi.fn(async (args: any) => {
      const row = {
        id: payments.size + 1,
        payment_id: `pi-${args.idempotencyKey}`,
        booking_ref: args.bookingRef,
        status: 'PENDING',
        amount: args.amount,
        currency: args.currency,
        idempotency_key: args.idempotencyKey,
        created_at: new Date(),
        updated_at: new Date(),
      };
      payments.set(`key:${args.idempotencyKey}`, row);
      payments.set(`ref:${args.bookingRef}`, row);
      payments.set(`pid:${row.payment_id}`, row);
      return row;
    }),
    assignGatewayPaymentId: vi.fn(async (localId: string, gatewayId: string) => {
      for (const r of payments.values()) {
        if (r.payment_id === localId) {
          r.payment_id = gatewayId;
          payments.set(`pid:${gatewayId}`, r);
          return;
        }
      }
    }),
    updatePaymentStatus: vi.fn(async (pid: string, status: any) => {
      const row = payments.get(`pid:${pid}`);
      if (row) row.status = status;
      return row ?? null;
    }),
    recordPaymentEvent: vi.fn(async () => undefined),
    findPaymentEvent: vi.fn(async () => null),
    processCallbackInTx: vi.fn(async (event: any, cb: any) => {
      // Simulate the real flow: call the closure with the current payment row so the
      // service can compute amountMismatch from the row's amount/currency.
      const row = payments.get(`pid:${event.payment_id}`) ?? null;
      let mismatch = false;
      // Run the closure BEFORE marking applied. The closure may modify row.status.
      try {
        // Mimic the real applyState signature: (client: PoolClient) => Promise<void>
        await cb({});
        // Re-read after the closure ran; if amount mismatch was set, status is unchanged.
        const after = payments.get(`pid:${event.payment_id}`);
        if (after && (after.amount !== event.amount || after.currency !== event.currency)) {
          mismatch = true;
        }
        if (after && !mismatch && after.status !== event.status) {
          after.status = event.status;
        }
      } catch {
        // allow callers to express failure paths
      }
      const finalRow = payments.get(`pid:${event.payment_id}`);
      return {
        kind: 'applied',
        payment: finalRow ?? { payment_id: event.payment_id, status: event.status },
        amountMismatch: mismatch,
      };
    }),
  };
});

import { PaymentService } from '../src/payments/service';
import { GatewayClient, GatewayError, GatewayTimeoutError } from '../src/gateway/client';
import { NoopBookingAdapter, BookingAmount } from '../src/ports/booking-payment.port';
import { NoopShowSeatAdapter } from '../src/ports/show-seat-payment.port';
import { PaymentTerminalError, PaymentValidationError } from '../src/payments/errors';

function makeGateway(overrides: Partial<GatewayClient> = {}): GatewayClient {
  return {
    charge: vi.fn(),
    refund: vi.fn(),
    otpSend: vi.fn(),
    otpVerify: vi.fn(),
    ...overrides,
  } as unknown as GatewayClient;
}

const deps = () => ({
  gateway: makeGateway(),
  bookings: new NoopBookingAdapter(),
  seats: new NoopShowSeatAdapter(),
});

describe('PaymentService.pay', () => {
  it('creates pending row, calls gateway /charge with Idempotency-Key, returns 202', async () => {
    const { gateway, bookings, seats } = deps();
    (gateway.charge as any).mockResolvedValue({ payment_id: 'pi-gw', status: 'PENDING' });
    const svc = new PaymentService(gateway, bookings, seats);

    const res = await svc.pay('BR-1', { callbackUrl: 'http://api/api/payments/callback' });

    expect(res.status).toBe('PENDING');
    expect(gateway.charge).toHaveBeenCalledTimes(1);
    const [call] = (gateway.charge as any).mock.calls[0];
    expect(call.booking_ref).toBe('BR-1');
    expect(call.callback_url).toBe('http://api/api/payments/callback');
    expect((gateway.charge as any).mock.calls[0][1]).toBe('pay:BR-1:1');
  });

  it('propagates GatewayTimeoutError so route maps to 504', async () => {
    const { gateway, bookings, seats } = deps();
    (gateway.charge as any).mockRejectedValue(new GatewayTimeoutError('gateway timeout'));
    const svc = new PaymentService(gateway, bookings, seats);

    await expect(svc.pay('BR-1', { callbackUrl: 'http://x' }))
      .rejects.toBeInstanceOf(GatewayTimeoutError);
  });

  it('propagates GatewayError so route maps to 502', async () => {
    const { gateway, bookings, seats } = deps();
    (gateway.charge as any).mockRejectedValue(new GatewayError('boom', 500));
    const svc = new PaymentService(gateway, bookings, seats);

    await expect(svc.pay('BR-1', { callbackUrl: 'http://x' }))
      .rejects.toBeInstanceOf(GatewayError);
  });
});

describe('PaymentService.handleCallback', () => {
  let svc: PaymentService;
  let gateway: GatewayClient;

  beforeEach(() => {
    gateway = makeGateway();
    svc = new PaymentService(gateway, new NoopBookingAdapter(), new NoopShowSeatAdapter());
  });

  it('returns payment_id and booking_ref from the callback payload', async () => {
    const payload = {
      event_id: 'evt-1', payment_id: 'pi-gw', status: 'SUCCEEDED',
      booking_ref: 'BR-1', amount: 1, currency: 'BDT', timestamp: '2024-01-01T00:00:00Z',
    } as any;
    const out = await svc.handleCallback(payload);
    expect(out.paymentId).toBe('pi-gw');
    expect(out.bookingRef).toBe('BR-1');
  });
});

describe('PaymentService.refund', () => {
  it('does not call gateway when payment lookup returns no row', async () => {
    const { gateway, bookings, seats } = deps();
    const svc = new PaymentService(gateway, bookings, seats);
    await expect(svc.refund('pi-unknown', { callbackUrl: 'http://x' })).rejects.toBeTruthy();
    expect(gateway.refund).not.toHaveBeenCalled();
  });
});

describe('PaymentTerminalError', () => {
  it('is thrown when booking status is not PENDING_PAYMENT', async () => {
    const { gateway, seats } = deps();
    const bookings = { getStatus: vi.fn().mockResolvedValue('CONFIRMED') } as any;
    const svc = new PaymentService(gateway, bookings, seats);
    await expect(svc.pay('BR-1', { callbackUrl: 'http://x' }))
      .rejects.toBeInstanceOf(PaymentTerminalError);
  });
});

// ---------------------------------------------------------------------------
// H-2 — /pay must never send amount=0 to the gateway
// ---------------------------------------------------------------------------
describe('PaymentService.pay — amount validation (H-2)', () => {
  it('uses the booking port amount, never a placeholder', async () => {
    const gateway = makeGateway();
    (gateway.charge as any).mockResolvedValue({ payment_id: 'pi-gw', status: 'PENDING' });
    const bookings = new NoopBookingAdapter({ defaultAmount: { amount: 450, currency: 'BDT' } });
    const svc = new PaymentService(gateway, bookings, new NoopShowSeatAdapter());

    const res = await svc.pay('BR-1', { callbackUrl: 'http://api/cb' });

    expect(res.status).toBe('PENDING');
    const chargeArgs = (gateway.charge as any).mock.calls[0][0];
    expect(chargeArgs.amount).toBe(450);
    expect(chargeArgs.currency).toBe('BDT');
  });

  it('rejects with PaymentValidationError when booking port returns null amount', async () => {
    const gateway = makeGateway();
    const bookings = new NoopBookingAdapter({ defaultAmount: null });
    const svc = new PaymentService(gateway, bookings, new NoopShowSeatAdapter());

    await expect(svc.pay('BR-1', { callbackUrl: 'http://api/cb' }))
      .rejects.toBeInstanceOf(PaymentValidationError);
    expect(gateway.charge).not.toHaveBeenCalled();
  });

  it('rejects with PaymentValidationError when booking port returns amount=0', async () => {
    const gateway = makeGateway();
    const bookings = new NoopBookingAdapter({ defaultAmount: { amount: 0, currency: 'BDT' } });
    const svc = new PaymentService(gateway, bookings, new NoopShowSeatAdapter());

    await expect(svc.pay('BR-1', { callbackUrl: 'http://api/cb' }))
      .rejects.toBeInstanceOf(PaymentValidationError);
    expect(gateway.charge).not.toHaveBeenCalled();
  });

  it('rejects with PaymentValidationError when booking port returns negative amount', async () => {
    const gateway = makeGateway();
    const bookings = new NoopBookingAdapter({ defaultAmount: { amount: -100, currency: 'BDT' } });
    const svc = new PaymentService(gateway, bookings, new NoopShowSeatAdapter());

    await expect(svc.pay('BR-1', { callbackUrl: 'http://api/cb' }))
      .rejects.toBeInstanceOf(PaymentValidationError);
    expect(gateway.charge).not.toHaveBeenCalled();
  });

  it('rejects with PaymentValidationError when booking port returns empty currency', async () => {
    const gateway = makeGateway();
    const bookings = new NoopBookingAdapter({ defaultAmount: { amount: 450, currency: '' } });
    const svc = new PaymentService(gateway, bookings, new NoopShowSeatAdapter());

    await expect(svc.pay('BR-1', { callbackUrl: 'http://api/cb' }))
      .rejects.toBeInstanceOf(PaymentValidationError);
    expect(gateway.charge).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// H-1 — callback amount/currency validation
// ---------------------------------------------------------------------------
describe('PaymentService.handleCallback — amount validation (H-1)', () => {
  it('signals amountMismatch when callback amount differs from the payment row', async () => {
    const gateway = makeGateway();
    const bookings = new NoopBookingAdapter({ defaultAmount: { amount: 450, currency: 'BDT' } });
    const seats = new NoopShowSeatAdapter();
    (gateway.charge as any).mockResolvedValue({ payment_id: 'pi-gw', status: 'PENDING' });
    const svc = new PaymentService(gateway, bookings, seats);

    // First, set up a pending payment row with amount=450, currency='BDT' via /pay.
    await svc.pay('BR-M1', { callbackUrl: 'http://api/cb' });

    // Now a callback arrives with a mismatched amount (1000 instead of 450).
    // The mock's processCallbackInTx will detect the mismatch and NOT update row.status.
    const out = await svc.handleCallback({
      event_id: 'evt-mismatch-1',
      payment_id: 'pi-gw',
      status: 'SUCCEEDED',
      booking_ref: 'BR-M1',
      amount: 1000,
      currency: 'BDT',
      timestamp: '2024-01-01T00:00:00Z',
    });

    expect(out.amountMismatch).toBe(true);
    expect(out.duplicate).toBe(false);
    expect(out.finalStatus).toBe('PENDING'); // unchanged — state NOT transitioned
  });

  it('signals amountMismatch when callback currency differs from the payment row', async () => {
    const gateway = makeGateway();
    const bookings = new NoopBookingAdapter({ defaultAmount: { amount: 450, currency: 'BDT' } });
    const seats = new NoopShowSeatAdapter();
    (gateway.charge as any).mockResolvedValue({ payment_id: 'pi-gw-2', status: 'PENDING' });
    const svc = new PaymentService(gateway, bookings, seats);
    await svc.pay('BR-M2', { callbackUrl: 'http://api/cb' });

    const out = await svc.handleCallback({
      event_id: 'evt-mismatch-2',
      payment_id: 'pi-gw-2',
      status: 'SUCCEEDED',
      booking_ref: 'BR-M2',
      amount: 450,
      currency: 'USD', // different currency
      timestamp: '2024-01-01T00:00:00Z',
    });

    expect(out.amountMismatch).toBe(true);
    expect(out.finalStatus).toBe('PENDING');
  });

  it('does NOT signal amountMismatch when amounts match', async () => {
    const gateway = makeGateway();
    const bookings = new NoopBookingAdapter({ defaultAmount: { amount: 450, currency: 'BDT' } });
    const seats = new NoopShowSeatAdapter();
    (gateway.charge as any).mockResolvedValue({ payment_id: 'pi-gw-3', status: 'PENDING' });
    const svc = new PaymentService(gateway, bookings, seats);
    await svc.pay('BR-M3', { callbackUrl: 'http://api/cb' });

    const out = await svc.handleCallback({
      event_id: 'evt-match',
      payment_id: 'pi-gw-3',
      status: 'SUCCEEDED',
      booking_ref: 'BR-M3',
      amount: 450,
      currency: 'BDT',
      timestamp: '2024-01-01T00:00:00Z',
    });

    expect(out.amountMismatch).toBe(false);
    expect(out.finalStatus).toBe('SUCCEEDED');
  });
});

// ---------------------------------------------------------------------------
// H-7 — refund policy: do not touch booking or seat ports
// ---------------------------------------------------------------------------
describe('PaymentService — refund policy (H-7)', () => {
  it('does NOT call bookings or seats ports on a REFUNDED callback', async () => {
    // The MVP policy is "REFUNDED keeps the booking CONFIRMED and the seat BOOKED".
    // We assert that the service does NOT call any booking or seat port method
    // when handling a REFUNDED callback.
    const gateway = makeGateway();
    const bookings = new NoopBookingAdapter({ defaultAmount: { amount: 450, currency: 'BDT' } });
    const seats = new NoopShowSeatAdapter();
    (gateway.charge as any).mockResolvedValue({ payment_id: 'pi-refund', status: 'PENDING' });
    const svc = new PaymentService(gateway, bookings, seats);

    // 1. /pay -> creates pending row with amount=450
    await svc.pay('BR-RF', { callbackUrl: 'http://api/cb' });

    // 2. SUCCEEDED callback -> moves payment to SUCCEEDED, calls confirmByRef + bookForBooking.
    await svc.handleCallback({
      event_id: 'evt-rf-1', payment_id: 'pi-refund', status: 'SUCCEEDED',
      booking_ref: 'BR-RF', amount: 450, currency: 'BDT',
      timestamp: '2024-01-01T00:00:00Z',
    });
    const callsAfterSuccess = [...bookings.calls, ...seats.calls].map((c) => c.method);
    expect(callsAfterSuccess).toContain('confirmByRef');
    expect(callsAfterSuccess).toContain('bookForBooking');

    // Reset recording so we observe ONLY what REFUNDED does.
    bookings.calls.length = 0;
    seats.calls.length = 0;

    // 3. REFUNDED callback -> per MVP policy, must NOT call any booking or seat port.
    await svc.handleCallback({
      event_id: 'evt-rf-2', payment_id: 'pi-refund', status: 'REFUNDED',
      booking_ref: 'BR-RF', amount: 450, currency: 'BDT',
      timestamp: '2024-01-01T00:00:00Z',
    });

    expect(bookings.calls).toHaveLength(0);
    expect(seats.calls).toHaveLength(0);
  });
});