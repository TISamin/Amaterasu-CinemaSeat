import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// Vite config:
// - Reads VITE_API_BASE_URL from the environment (set in Dockerfile build arg,
//   defaults to "/api" so the SPA always talks to the same origin and the
//   nginx reverse proxy in this same image forwards /api/* to the backend).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiBaseUrl = env.VITE_API_BASE_URL || '/api';

  return {
    plugins: [react()],
    // Use a relative base so the built `dist/` works when served from any
    // subpath (e.g. Live Server at http://127.0.0.1:5500/frontend/dist/).
    // Inside Docker, nginx still reverse-proxies /api/* on the same origin,
    // so a relative base is fine in both cases.
    base: './',
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:3000',
          changeOrigin: true,
        },
      },
    },
    define: {
      __API_BASE_URL__: JSON.stringify(apiBaseUrl),
    },
  };
});