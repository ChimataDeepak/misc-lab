# frontend — React 18 + TypeScript + Vite

**Skill:** React (hooks, custom hooks, component tests) with TypeScript.

**What I built:** one page (`src/App.tsx`) with a `ReceiptForm` (payer, amount, currency) and a list of receipts created this session. `useReceipts` is a custom hook holding `receipts`, `loading`, `error` and an `add()` that POSTs to `http://localhost:8090/api/receipts`. Vitest + React Testing Library test the form and the hook with a mocked `fetch`.

**Why it matters:** backend roles increasingly expect you to read and ship a small UI and to test it without a browser. A custom hook keeps data and state logic out of the view, so it can be tested on its own.

**Interview line:** "I kept the network and state logic in a `useReceipts` hook and the form as a plain component that gets `onSubmit` and `loading` as props. That let me unit test the hook with a stubbed `fetch` and the form with RTL, with no backend running."

```bash
npm install
npm test -- --run     # 4 tests
npm run build
npm run dev           # http://localhost:5173
VITE_API_URL=/api/receipts npm run dev   # same-origin through the Vite proxy (for Selenium)
```
