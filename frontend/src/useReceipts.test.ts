import { renderHook, act } from '@testing-library/react';
import { useReceipts } from './useReceipts';

afterEach(() => vi.unstubAllGlobals());

test('adds the created receipt on success', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: true, json: async () => ({ receiptId: 'r1', payer: 'Asha', amount: 500, currency: 'INR', amountUsd: 6 }),
  }));
  const { result } = renderHook(() => useReceipts());
  await act(async () => { await result.current.add({ payer: 'Asha', amount: 500, currency: 'INR' }); });
  expect(result.current.receipts).toHaveLength(1);
  expect(result.current.error).toBeNull();
  expect(result.current.loading).toBe(false);
});

test('sets error on HTTP failure', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 400 }));
  const { result } = renderHook(() => useReceipts());
  await act(async () => { await result.current.add({ payer: '', amount: 0, currency: 'INR' }); });
  expect(result.current.receipts).toHaveLength(0);
  expect(result.current.error).toBe('Request failed: 400');
});
