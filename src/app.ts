import Fastify, { FastifyInstance, FastifyRequest } from 'fastify';
import { AppConfig } from './config/env';
import { GatewayClient } from './gateway/client';
import { BookingPaymentPort } from './ports/booking-payment.port';
import { ShowSeatPaymentPort } from './ports/show-seat-payment.port';
import { PaymentService } from './payments/service';
import { registerRoutes } from './http/routes';

export interface AppDeps {
  cfg: AppConfig;
  gateway: GatewayClient;
  bookings: BookingPaymentPort;
  seats: ShowSeatPaymentPort;
}

/**
 * Build a Fastify instance wired with Agent 2 routes. No `listen` is called here —
 * the caller (server.ts or tests) decides when to bind.
 */
export function buildApp(deps: AppDeps): FastifyInstance {
  const app = Fastify({
    logger: false,
    bodyLimit: 1024 * 1024,
  });

  // Per-route raw body capture. Routes opt in via { config: { rawBody: true } }.
  app.addContentTypeParser(
    'application/json',
    { parseAs: 'string' },
    (req: FastifyRequest, body: string, done) => {
      const routeConfig = (req.routeOptions?.config as { rawBody?: boolean } | undefined) ?? {};
      if (routeConfig.rawBody) {
        (req as unknown as { rawBody: string }).rawBody = body;
      }
      try {
        const parsed = body ? JSON.parse(body) : undefined;
        done(null, parsed);
      } catch (err) {
        done(err as Error, undefined);
      }
    },
  );

  const service = new PaymentService(deps.gateway, deps.bookings, deps.seats);
  registerRoutes(app, { cfg: deps.cfg, service });

  return app;
}