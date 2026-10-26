import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// En desarrollo (npm run dev) se reenvían las llamadas a los servicios corriendo en local.
// En Docker esto lo hace nginx (ver nginx.conf).
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": "http://localhost:8000",
      "/graphql": "http://localhost:4000",
    },
  },
});
