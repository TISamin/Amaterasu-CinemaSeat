import { request, Dispatcher } from 'undici';
import { AppConfig } from '../config/env';
import {
  ChargeRequest,
  ChargeResponse,
  OtpSendRequest,
  OtpSendResponse,
  OtpVerifyRequest,
  OtpVerifyResponse,
  RefundRequest,
  RefundResponse,
} from '../payments/types';

export class GatewayError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly body?: unknown,
  ) {
    super(message);
    this.name = 'GatewayError';
  }
}

export class GatewayTimeoutError extends Error {
  constructor(message = 'gateway timeout') {
    super(message);
    this.name = 'GatewayTimeoutError';
  }
}

export interface GatewayClientOptions {
  baseUrl: string;
  pathPrefix: string;
  timeoutMs: number;
  /** Optional dispatch used for testing (e.g. MockPool). Defaults to a fresh undici agent. */
  dispatcher?: Dispatcher;
}

export class GatewayClient {
  constructor(private readonly opts: GatewayClientOptions) {}

  private url(path: string): string {
    const base = this.opts.baseUrl.replace(/\/+$/, '');
    const prefix = this.opts.pathPrefix.startsWith('/') ? this.opts.pathPrefix : `/${this.opts.pathPrefix}`;
    const cleanPath = path.startsWith('/') ? path : `/${path}`;
    if (this.opts.pathPrefix === '') return `${base}${cleanPath}`;
    return `${base}${prefix}${cleanPath}`;
  }

  private async postJson<TReq, TRes>(
    path: string,
    body: TReq,
    headers: Record<string, string>,
    mockForce?: string,
  ): Promise<TRes> {
    const payload = JSON.stringify(body);
    const h: Record<string, string> = {
      'content-type': 'application/json',
      ...headers,
    };
    if (mockForce) h['x-mock-force'] = mockForce;
    const res = await request(this.url(path), {
      method: 'POST',
      body: payload,
      headers: h,
      dispatcher: this.opts.dispatcher,
      bodyTimeout: this.opts.timeoutMs,
      headersTimeout: this.opts.timeoutMs,
    });
    const text = await res.body.text();
    let json: unknown = undefined;
    if (text) {
      try { json = JSON.parse(text); } catch { json = text; }
    }
    if (res.statusCode >= 500) {
      throw new GatewayError(`gateway ${res.statusCode}`, res.statusCode, json);
    }
    if (res.statusCode >= 400) {
      throw new GatewayError(`gateway ${res.statusCode}`, res.statusCode, json);
    }
    return json as TRes;
  }

  async charge(
    req: ChargeRequest,
    idempotencyKey: string,
    mockForce?: string,
  ): Promise<ChargeResponse> {
    return this.postJson<ChargeRequest, ChargeResponse>(
      '/charge',
      req,
      { 'idempotency-key': idempotencyKey },
      mockForce,
    );
  }

  async refund(req: RefundRequest, mockForce?: string): Promise<RefundResponse> {
    return this.postJson<RefundRequest, RefundResponse>('/refund', req, {}, mockForce);
  }

  async otpSend(req: OtpSendRequest): Promise<OtpSendResponse> {
    return this.postJson<OtpSendRequest, OtpSendResponse>('/otp/send', req, {});
  }

  async otpVerify(req: OtpVerifyRequest): Promise<OtpVerifyResponse> {
    return this.postJson<OtpVerifyRequest, OtpVerifyResponse>('/otp/verify', req, {});
  }
}

export function makeGatewayClient(cfg: AppConfig, dispatcher?: Dispatcher): GatewayClient {
  return new GatewayClient({
    baseUrl: cfg.gatewayBaseUrl,
    pathPrefix: cfg.gatewayPathPrefix,
    timeoutMs: cfg.gatewayTimeoutMs,
    dispatcher,
  });
}
