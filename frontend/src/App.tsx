import { useEffect } from 'react';
import { ReceiptForm } from './ReceiptForm';
import { useReceipts } from './useReceipts';

export default function App() {
  const { receipts, loading, error, add } = useReceipts();

  // useEffect: keep the tab title in sync with how many receipts exist.
  useEffect(() => { document.title = `Receipts (${receipts.length})`; }, [receipts.length]);

  return (
    <main style={{ fontFamily: 'sans-serif', maxWidth: 480, margin: '2rem auto', padding: '0 16px' }}>
      <h1>Receipts</h1>
      <ReceiptForm onSubmit={add} loading={loading} />
      {error && <p role="alert" id="error" style={{ color: 'crimson' }}>{error}</p>}
      <ul id="receipts">
        {receipts.map((r) => (
          <li key={r.receiptId}>{r.payer}: {r.amount} {r.currency}{r.amountUsd != null && ` (= ${r.amountUsd} USD)`}</li>
        ))}
      </ul>
    </main>
  );
}
