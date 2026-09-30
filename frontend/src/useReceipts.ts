import { useCallback, useState } from 'react';
import { createReceipt, Receipt, ReceiptInput } from './api';

/** Holds the receipts created this session plus loading/error state. */
export function useReceipts() {
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const add = useCallback(async (input: ReceiptInput) => {
    setLoading(true);
    setError(null);
    try {
      const created = await createReceipt(input);
      setReceipts((prev) => [created, ...prev]);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error');
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  return { receipts, loading, error, add };
}
