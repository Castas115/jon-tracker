import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { VitePWA } from 'vite-plugin-pwa';

// In docker dev compose this is set to http://backend:8000 (compose DNS).
// Native dev (uvicorn on host) defaults to localhost.
const backendUrl = process.env.VITE_BACKEND_URL ?? 'http://127.0.0.1:8000';

export default defineConfig({
  plugins: [
    svelte(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg'],
      manifest: {
        name: 'Jon Tracker',
        short_name: 'Tracker',
        description: 'Weekly tasks and calendar',
        lang: 'en',
        theme_color: '#0f172a',
        background_color: '#0f172a',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any maskable' }
        ]
      }
    })
  ],
  server: {
    host: true,
    watch: { usePolling: true },
    proxy: {
      '/tasks': backendUrl,
      '/health': backendUrl,
      '/auth': backendUrl,
      '/calendar': backendUrl
    }
  }
});
