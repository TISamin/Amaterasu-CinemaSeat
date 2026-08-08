/**
 * Lightweight env validation without bringing in zod as a runtime dep.
 * Throws a readable error on misconfiguration.
 */
function num(v: string | undefined, dflt: number): number {
  if (v === undefined || v === '') return dflt;
  const n = Number(v);
  if (!Number.isFinite(n)) throw new Error(`env: not a number: ${v}`);
  return n;
}
function str(v: string | undefined, dflt: string): string {
  if (v === undefined || v === '') return dflt;
  return v;
}
function bool(v: string | undefined, dflt: boolean): boolean {
  if (v === undefined || v === '') return dflt;
  if (v === '1' || v.toLowerCase() === 'true') return true;
  if (v === '0' || v.toLowerCase() === 'false') return false;
  throw new Error(`env: not a boolean: ${v}`);
}

export interface AppConfig {
  apiHost: string;
  apiPort: number;
  backendServiceName: string;
  databaseUrl: string;
  gatewayBaseUrl: string;
  gatewayPathPrefix: string;
  gatewayTimeoutMs: number;
  gatewaySecret: string | undefined;
  hmacEnabled: boolean;
  signatureEncoding: 'hex' | 'base64';
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  const cfg: AppConfig = {
    apiHost: str(env.API_HOST, '0.0.0.0'),
    apiPort: num(env.API_PORT, 3000),
    backendServiceName: str(env.BACKEND_SERVICE_NAME, 'api'),
    databaseUrl: str(env.DATABASE_URL, 'postgres://postgres:postgres@localhost:5432/cinemaseat'),
    gatewayBaseUrl: str(env.GATEWAY_BASE_URL, 'http://gateway:9000'),
    gatewayPathPrefix: str(env.GATEWAY_PATH_PREFIX, ''),
    gatewayTimeoutMs: num(env.GATEWAY_TIMEOUT_MS, 5000),
    gatewaySecret: env.GATEWAY_SECRET && env.GATEWAY_SECRET !== '' ? env.GATEWAY_SECRET : undefined,
    hmacEnabled: bool(env.HMAC_ENABLED, false),
    signatureEncoding: (env.SIGNATURE_ENCODING === 'base64' ? 'base64' : 'hex'),
  };
  if (cfg.hmacEnabled && !cfg.gatewaySecret) {
    throw new Error('HMAC_ENABLED=true requires GATEWAY_SECRET');
  }
  return cfg;
}

export function buildCallbackUrl(cfg: AppConfig, path = '/api/payments/callback'): string {
  const safePath = path.startsWith('/') ? path : `/${path}`;
  return `http://${cfg.backendServiceName}:${cfg.apiPort}${safePath}`;
}