export interface ReceiptInput { payer: string; amount: number; currency: string; }
export interface Receipt extends ReceiptInput { receiptId: string; amountUsd?: number; }

// Default per contract; set VITE_API_URL=/api/receipts to go through the Vite proxy (used by the Selenium test).
export const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8090/api/receipts';

export async function createReceipt(input: ReceiptInput): Promise<Receipt> {
  const res = await fetch(API_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  if (!res.ok) throw new Error(`Request failed: ${res.status}`);
  return res.json();
}
