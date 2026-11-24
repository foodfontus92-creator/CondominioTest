import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server proxies API + STOMP WebSocket to the local Spring Boot backend.
// In production the Nginx container does the same (see nginx.conf).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true, changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 900,
  },
});
