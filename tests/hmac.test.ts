import { describe, it, expect } from 'vitest';
import { signBody, verifySignature } from '../src/gateway/hmac';

describe('HMAC sign/verify', () => {
  const secret = 'shhh';
  const body = '{"hello":"world"}';

  it('verifies hex signature', () => {
    const sig = signBody(body, secret, 'hex');
    expect(verifySignature(body, sig, secret, 'hex')).toBe(true);
  });

  it('verifies base64 signature', () => {
    const sig = signBody(body, secret, 'base64');
    expect(verifySignature(body, sig, secret, 'base64')).toBe(true);
  });

  it('rejects tampered body', () => {
    const sig = signBody(body, secret, 'hex');
    expect(verifySignature(body + 'x', sig, secret, 'hex')).toBe(false);
  });

  it('rejects wrong secret', () => {
    const sig = signBody(body, secret, 'hex');
    expect(verifySignature(body, sig, 'different', 'hex')).toBe(false);
  });
});