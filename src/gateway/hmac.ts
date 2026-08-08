import { createHmac, timingSafeEqual } from 'crypto';

/**
 * HMAC-SHA256 over the raw request body, encoded as hex (lowercase) or base64.
 * Used to verify the X-Signature header from the gateway.
 *
 * IMPORTANT: the caller MUST pass the raw request body bytes (Buffer or string),
 * NOT a re-serialized JSON object. Re-serializing can change byte order and
 * produce a valid signature mismatch.
 */
export function verifySignature(
  rawBody: string | Buffer,
  signature: string,
  secret: string,
  encoding: 'hex' | 'base64' = 'hex',
): boolean {
  if (!signature) return false;
  const expected = createHmac('sha256', secret).update(rawBody).digest();
  let received: Buffer;
  try {
    received = Buffer.from(signature, encoding);
  } catch {
    return false;
  }
  if (received.length !== expected.length) return false;
  try {
    return timingSafeEqual(expected, received);
  } catch {
    return false;
  }
}

export function signBody(
  rawBody: string | Buffer,
  secret: string,
  encoding: 'hex' | 'base64' = 'hex',
): string {
  return createHmac('sha256', secret).update(rawBody).digest(encoding);
}