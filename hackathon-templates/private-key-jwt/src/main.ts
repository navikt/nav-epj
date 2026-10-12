import { readConfig } from "./config.ts";
import { buildApp } from "./app.ts";

const config = readConfig();
const app = await buildApp(config, process.argv.includes("--reference"));
await app.listen({ port: config.port, host: "127.0.0.1" });
