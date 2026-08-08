import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { AppConfig, buildCallbackUrl } from '../config/env';
import { verifySignature } from '../gateway/hmac';
import { GatewayError, GatewayTimeoutError } from '../gateway/client';
import { PaymentService } from '../payments/service';
import { CallbackPayload } from '../payments/types';
import { PaymentValidationError } from '../payments/errors';

interface PayParams { bookingRef: string; }
interface RefundParams { paymentId: string; }
interface OtpSendBody { phone: string; ref: string; }
interface OtpVerifyBody { ref: string; code: string; }

export interface RouteDeps {
  cfg: AppConfig;
  service: PaymentService;
}

/**
 * Registers all Agent 2 routes. /health is included so docker-compose / liveness
 * checks work without depending on the gateway (API_CONTRACT.md §1 + §8).
 */
export function registerRoutes(app: FastifyInstance, deps: RouteDeps): void {
  const { cfg, service } = deps;

  // ---- /health (no gateway dependency) ------------------------------------
  app.get('/health', async () => ({ status: 'UP' }));

  // ---- /api/bookings/{bookingRef}/pay -------------------------------------
  app.post<{ Params: PayParams }>(
    '/api/bookings/:bookingRef/pay',
    async (req: FastifyRequest<{ Params: PayParams }>, reply: FastifyReply) => {
      const { bookingRef } = req.params;
      const mockForce = (req.headers['x-mock-force'] as string | undefined) || undefined;
      try {
        const result = await service.pay(bookingRef, {
          mockForce,
          callbackUrl: buildCallbackUrl(cfg),
        });
        reply.code(202).send({
          bookingRef: result.bookingRef,
          paymentId: result.paymentId,
          status: result.status,
        });
      } catch (err) {
        reply.send(mapPayError(err, reply));
      }
    },
  );

  // ---- /api/payments/callback ---------------------------------------------
  // Register a per-route content-type parser that captures the raw body bytes
  // so we can verify HMAC against the exact bytes the gateway signed.
  app.post(
    '/api/payments/callback',
    {
      config: { rawBody: true },
    },
    async (req, reply) => {
      const rawBody = (req as unknown as { rawBody?: string }).rawBody ?? '';
      const sig = req.headers['x-signature'] as string | undefined;

      if (cfg.hmacEnabled) {
        if (!cfg.gatewaySecret || !sig || !verifySignature(rawBody, sig, cfg.gatewaySecret, cfg.signatureEncoding)) {
          reply.code(401).send({ error: 'INVALID_SIGNATURE', message: 'X-Signature failed verification' });
          return;
        }
      }

      let parsed: unknown;
      try {
        parsed = rawBody ? JSON.parse(rawBody) : undefined;
      } catch {
        reply.code(400).send({ error: 'INVALID_JSON', message: 'callback body is not valid JSON' });
        return;
      }

      const v = validateCallbackPayload(parsed);
      if (!v.ok) {
        reply.code(400).send({ error: 'INVALID_PAYLOAD', message: v.error });
        return;
      }

      const result = await service.handleCallback(v.value);
      // Always 200 for duplicate; the gateway interprets non-2xx as failure.
      // `amountMismatch` signals a callback whose amount/currency didn't match the
      // payment row (data integrity / tampering). The gateway still receives 200
      // so it doesn't retry; the operator is expected to alert on this.
      reply.code(200).send({
        ok: true,
        duplicate: result.duplicate,
        paymentId: result.paymentId,
        bookingRef: result.bookingRef,
        status: result.finalStatus,
        amountMismatch: result.amountMismatch,
      });
    },
  );

  // ---- /api/payments/{paymentId}/refund -----------------------------------
  app.post<{ Params: RefundParams }>(
    '/api/payments/:paymentId/refund',
    async (req: FastifyRequest<{ Params: RefundParams }>, reply: FastifyReply) => {
      const { paymentId } = req.params;
      const mockForce = (req.headers['x-mock-force'] as string | undefined) || undefined;
      try {
        const r = await service.refund(paymentId, { mockForce, callbackUrl: buildCallbackUrl(cfg) });
        reply.code(202).send(r);
      } catch (err) {
        reply.send(mapGatewayError(err, reply));
      }
    },
  );

  // ---- /api/otp/send ------------------------------------------------------
  app.post<{ Body: OtpSendBody }>(
    '/api/otp/send',
    async (req: FastifyRequest<{ Body: OtpSendBody }>, reply: FastifyReply) => {
      const body = req.body;
      if (!body || !body.phone || !body.ref) {
        reply.code(400).send({ error: 'INVALID_PAYLOAD', message: 'phone and ref are required' });
        return;
      }
      try {
        const r = await service.otpSend(body);
        reply.code(200).send(r);
      } catch (err) {
        reply.send(mapGatewayError(err, reply));
      }
    },
  );

  // ---- /api/otp/verify ----------------------------------------------------
  app.post<{ Body: OtpVerifyBody }>(
    '/api/otp/verify',
    async (req: FastifyRequest<{ Body: OtpVerifyBody }>, reply: FastifyReply) => {
      const body = req.body;
      if (!body || !body.ref || !body.code) {
        reply.code(400).send({ error: 'INVALID_PAYLOAD', message: 'ref and code are required' });
        return;
      }
      try {
        const r = await service.otpVerify(body);
        reply.code(200).send(r);
      } catch (err) {
        reply.send(mapGatewayError(err, reply));
      }
    },
  );
}

// ---- error mappers ---------------------------------------------------------

function mapPayError(err: unknown, reply: FastifyReply): { error: string; message: string } | undefined {
  if (err instanceof Error && err.name === 'PaymentNotFoundError') {
    reply.code(404);
    return { error: 'BOOKING_NOT_FOUND', message: err.message };
  }
  if (err instanceof Error && err.name === 'PaymentTerminalError') {
    reply.code(409);
    return { error: 'PAYMENT_TERMINAL', message: err.message };
  }
  if (err instanceof PaymentValidationError) {
    reply.code(400);
    return { error: 'PAYMENT_VALIDATION', message: err.message };
  }
  if (err instanceof GatewayTimeoutError) {
    reply.code(504);
    return { error: 'GATEWAY_TIMEOUT', message: err.message };
  }
  if (err instanceof GatewayError) {
    reply.code(502);
    return { error: 'GATEWAY_ERROR', message: err.message };
  }
  throw err;
}

function mapGatewayError(err: unknown, reply: FastifyReply): { error: string; message: string } | undefined {
  if (err instanceof Error && err.name === 'PaymentNotFoundError') {
    reply.code(404);
    return { error: 'PAYMENT_NOT_FOUND', message: err.message };
  }
  if (err instanceof Error && err.name === 'PaymentTerminalError') {
    reply.code(409);
    return { error: 'PAYMENT_NOT_REFUNDABLE', message: err.message };
  }
  if (err instanceof GatewayTimeoutError) {
    reply.code(504);
    return { error: 'GATEWAY_TIMEOUT', message: err.message };
  }
  if (err instanceof GatewayError) {
    reply.code(502);
    return { error: 'GATEWAY_ERROR', message: err.message };
  }
  throw err;
}

type ValidationResult<T> = { ok: true; value: T } | { ok: false; error: string };

function validateCallbackPayload(v: unknown): ValidationResult<CallbackPayload> {
  if (!v || typeof v !== 'object') return { ok: false, error: 'body must be an object' };
  const r = v as Record<string, unknown>;
  const required = ['event_id', 'payment_id', 'booking_ref', 'status', 'amount', 'currency', 'timestamp'];
  for (const k of required) {
    if (!(k in r)) return { ok: false, error: `missing field: ${k}` };
  }
  const status = String(r['status']);
  if (!['SUCCEEDED', 'FAILED', 'REFUNDED'].includes(status)) {
    return { ok: false, error: `invalid status: ${status}` };
  }
  return {
    ok: true,
    value: {
      event_id: String(r['event_id']),
      payment_id: String(r['payment_id']),
      booking_ref: String(r['booking_ref']),
      status: status as CallbackPayload['status'],
      amount: Number(r['amount']),
      currency: String(r['currency']),
      timestamp: String(r['timestamp']),
    },
  };
}