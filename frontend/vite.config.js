import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { tanstackRouter } from "@tanstack/router-plugin/vite";

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
      "@data": "/src/data",
    },
  },
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/epj": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/fhir": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    },
  },
});
