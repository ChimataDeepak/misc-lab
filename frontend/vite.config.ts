/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // allowedHosts: the Selenium browser container reaches this dev server as host.testcontainers.internal
  server: { port: 5173, host: true, allowedHosts: ['host.testcontainers.internal'], proxy: {
    '/api': {
      target: 'http://localhost:8090',
      // Same-origin from the browser's view, so CORS doesn't apply; drop Origin so the backend
      // doesn't reject callers like host.testcontainers.internal that aren't in its CORS allow-list.
      configure: (proxy) => proxy.on('proxyReq', (req) => req.removeHeader('origin')),
    },
  }  },
  test: { environment: 'jsdom', globals: true, setupFiles: './src/setupTests.ts' },
});
