import { defineConfig, mergeConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default mergeConfig(
  defineConfig({
    plugins: [react()],
    resolve: {
      alias: {
        "@utils": "/src/utils",
        "@data": "/src/data",
      },
    },
  }),
  defineConfig({
    test: {
      environment: "jsdom",
      globals: false,
      setupFiles: ["./src/xp/testSetup.ts"],
      include: ["src/**/*.test.{ts,tsx}"],
      css: false,
    },
  }),
);
