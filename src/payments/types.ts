export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED';
export type CallbackStatus = 'SUCCEEDED' | 'FAILED' | 'REFUNDED';

export interface PaymentRow {
  id: number;
  payment_id: string;
  booking_ref: string;
  status: PaymentStatus;
  amount: number;
  currency: string;
  idempotency_key: string;
  created_at: Date;
  updated_at: Date;
}

export interface PaymentEventRow {
  id: number;
  event_id: string;
  payment_id: string;
  booking_ref: string;
  status: CallbackStatus;
  amount: number;
  currency: string;
  received_at: Date;
  processed_at: Date | null;
}

export interface ChargeRequest {
  amount: number;
  currency: string;
  booking_ref: string;
  callback_url: string;
}

export interface ChargeResponse {
  payment_id: string;
  status: 'PENDING';
}

export interface RefundRequest {
  payment_id: string;
  amount?: number;
  reason?: string;
}

export interface RefundResponse {
  refund_id: string;
  status: 'REFUNDED' | 'PENDING';
}

export interface OtpSendRequest {
  phone: string;
  ref: string;
}

export interface OtpSendResponse {
  ok: boolean;
  /** Optional gateway-provided session ref. May differ from the client-supplied ref. */
  session_ref?: string;
  error?: string;
}

export interface OtpVerifyRequest {
  ref: string;
  code: string;
}

export interface OtpVerifyResponse {
  verified: boolean;
}

export interface CallbackPayload {
  event_id: string;
  payment_id: string;
  booking_ref: string;
  status: CallbackStatus;
  amount: number;
  currency: string;
  timestamp: string;
}