import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { tanstackRouter } from "@tanstack/router-plugin/vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    tanstackRouter({
      target: "react",
      autoCodeSplitting: true,
    }),
    react(),
  ],
  resolve: {
    alias: {
      "@utils": "/src/utils",
    },
  },
  server: {
    proxy: {
      "/fhir": {
        target: "http://localhost:8080", // Your Ktor server port
        changeOrigin: true,
      },
    },
  },
});
