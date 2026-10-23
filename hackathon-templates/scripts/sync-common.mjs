import { copyFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// Maintainer-only: copies reviewed scaffolding, never participant credentials or node_modules.
const root = fileURLToPath(new URL("../", import.meta.url));
const common = [
  "package.json", "package-lock.json", "tsconfig.json", ".gitignore", ".npmrc", ".nvmrc",
  "GUIDE.md", "EXERCISES.md", "PROGRESSION.md", "src/config.ts", "src/http.ts", "src/reference.ts",
  "src/extensions.ts", "src/server.ts", "src/main.ts", "test/fixture.ts", "test/helpers.test.ts",
  "test/README.md",
];
const ehr = [
  "src/app.ts", "src/steps.ts", "test/launch.test.ts", "test/clinical.test.ts",
  "test/scopes.test.ts", "test/auth-errors.test.ts", "test/writeback.test.ts",
];
for (const variant of ["client-secret", "private-key-jwt", "backend-services"]) {
  const files = variant === "backend-services" ? common : [...common, ...ehr];
  for (const file of files) {
    const target = resolve(root, variant, file);
    await mkdir(dirname(target), { recursive: true });
    await copyFile(resolve(root, "public", file), target);
  }
}
console.log("Synchronized common scaffolding. Each variant is still a standalone npm project.");
