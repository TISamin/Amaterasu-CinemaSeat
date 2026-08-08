import { loadConfig } from './config/env';
import { initPool, closePool } from './db/pool';
import { buildApp } from './app';
import { makeGatewayClient } from './gateway/client';
import { NoopBookingAdapter, BookingPaymentPort } from './ports/booking-payment.port';
import { NoopShowSeatAdapter, ShowSeatPaymentPort } from './ports/show-seat-payment.port';

async function main(): Promise<void> {
  const cfg = loadConfig();
  initPool({ connectionString: cfg.databaseUrl });

  // Until Agent 1 lands, we use the no-op adapters. Agent 1 wires the real ones in.
  const bookings: BookingPaymentPort = new NoopBookingAdapter();
  const seats: ShowSeatPaymentPort = new NoopShowSeatAdapter();

  const gateway = makeGatewayClient(cfg);
  const app = buildApp({ cfg, gateway, bookings, seats });

  await app.listen({ host: cfg.apiHost, port: cfg.apiPort });
  // eslint-disable-next-line no-console
  console.log(`[agent-2] listening on http://${cfg.apiHost}:${cfg.apiPort}`);

  const shutdown = async () => {
    try { await app.close(); } catch { /* ignore */ }
    try { await closePool(); } catch { /* ignore */ }
    process.exit(0);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

main().catch((err) => {
  // eslint-disable-next-line no-console
  console.error('[agent-2] startup failed', err);
  process.exit(1);
});