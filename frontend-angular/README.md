# Angular lookup UI (tiny)

One standalone component: type a receipt id, `GET /api/receipts/{id}` from the Java receipt service.

```
cd frontend-angular && npm test
```

This is **not** a production Angular app. No router, no NgRx. Enough to say: component + injectable API + HTTP to a Spring Boot backend.

## Interview
"React is already in misc-lab. This folder is the Angular flavour Citi/Broadridge titles ask for: a standalone component binds an input signal, calls the same REST receipt API, and renders the JSON. I would not claim Angular material, SSR, or a design system."
