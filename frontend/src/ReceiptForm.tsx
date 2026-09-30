import { FormEvent, useState } from 'react';
import { ReceiptInput } from './api';

interface Props { onSubmit: (r: ReceiptInput) => Promise<boolean>; loading: boolean; }

export function ReceiptForm({ onSubmit, loading }: Props) {
  const [payer, setPayer] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState('INR');

  async function handle(e: FormEvent) {
    e.preventDefault();
    const ok = await onSubmit({ payer, amount: Number(amount), currency });
    if (ok) { setPayer(''); setAmount(''); }
  }

  return (
    <form onSubmit={handle} aria-label="receipt form">
      <label>Payer <input id="payer" value={payer} onChange={(e) => setPayer(e.target.value)} required /></label>
      <label>Amount <input id="amount" type="number" min="0.01" step="0.01" value={amount}
        onChange={(e) => setAmount(e.target.value)} required /></label>
      <label>Currency
        <select id="currency" value={currency} onChange={(e) => setCurrency(e.target.value)}>
          <option>INR</option><option>USD</option><option>EUR</option>
        </select>
      </label>
      <button id="submit" type="submit" disabled={loading}>{loading ? 'Saving...' : 'Create receipt'}</button>
    </form>
  );
}
