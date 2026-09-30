export class ReceiptApi {
  constructor(private readonly baseUrl: string, private readonly fetchFn: typeof fetch = fetch) {}

  async get(id: string): Promise<string> {
    const res = await this.fetchFn(`${this.baseUrl}/api/receipts/${encodeURIComponent(id)}`);
    if (res.status === 404) return 'not found';
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return JSON.stringify(await res.json(), null, 2);
  }
}
