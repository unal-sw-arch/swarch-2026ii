import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    port: 5172,
    host: '0.0.0.0',
    allowedHosts: true, // Permite túneles de Cloudflare (.trycloudflare.com)
  },
  preview: {
    port: 5172,
    host: '0.0.0.0',
    allowedHosts: true,
  },
  build: {
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router'],
          maps: ['@vis.gl/react-google-maps'],
          firebase: ['firebase/app', 'firebase/auth'],
        },
      },
    },
  },
});
