import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    tanstackRouter({
      target: "react",
      autoCodeSplitting: true,
    }),
    tailwindcss(),
    react(),
    
  ],
  resolve: {
    alias: {
      "@utils": "/src/utils",
      "@data": "/src/data",
      "@components": "/src/components/*"
    },
  },
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/epj": {
        target: "http://localhost:8080", // Your Ktor server port
        changeOrigin: true,
      },
      "/fhir": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    },
  },
});
