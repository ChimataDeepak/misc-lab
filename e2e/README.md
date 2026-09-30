# e2e — Selenium WebDriver + Testcontainers

**Skill:** Selenium WebDriver UI automation with the Page Object pattern.

**What I built:** `ReceiptFormIT` starts Chrome in a Testcontainers `BrowserWebDriverContainer`, opens the Vite app at `host.testcontainers.internal:5173`, creates a receipt through `ReceiptFormPage` and waits for it to appear in the list.

**Why it matters:** a containerised browser gives the same Chrome on a laptop and in CI, with no local driver setup. A Page Object keeps selectors in one place, so a UI change means one fix, not one fix per test.

**Interview line:** "Chrome runs in Docker, so it can't see the host's `localhost`. I exposed the host ports with `Testcontainers.exposeHostPorts` and pointed the browser at `host.testcontainers.internal`. The dev server proxies `/api` to the backend, so the browser only talks to one origin and CORS isn't involved."

Run it (Docker required). Start the backend on :8090, then:
```bash
(cd ../frontend && VITE_API_URL=/api/receipts npm run dev) &
mvn verify      # failsafe runs *IT
```
