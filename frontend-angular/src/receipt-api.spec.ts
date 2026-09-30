import { describe, expect, it, vi } from 'vitest';
import { ReceiptApi } from './receipt-api';

describe('ReceiptApi', () => {
  it('returns JSON for 200', async () => {
    const fetchFn = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ payer: 'deepak', usdAmount: '12.00' }),
    });
    const api = new ReceiptApi('http://localhost:8090', fetchFn as unknown as typeof fetch);
    await expect(api.get('r1')).resolves.toContain('deepak');
  });

  it('maps 404 to not found', async () => {
    const fetchFn = vi.fn().mockResolvedValue({ ok: false, status: 404, json: async () => ({}) });
    const api = new ReceiptApi('http://localhost:8090', fetchFn as unknown as typeof fetch);
    await expect(api.get('missing')).resolves.toBe('not found');
  });
});
