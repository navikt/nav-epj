import { unfinished } from "./config.ts";
import type * as reference from "./reference.ts";

export type Steps = Pick<typeof reference, "discover" | "backendToken" | "backendSearch">;

export const steps: Steps = {
  async discover(config) {
    return unfinished(1, "Discover the trusted token endpoint and check client_credentials/private_key_jwt support");
  },
  async backendToken(config, discovery) {
    return unfinished(2, "Sign a fresh ES384 client assertion and request a patient-less token with system scopes");
  },
  async backendSearch(config, tokens) {
    return unfinished(3, "Search Patient by the supplied synthetic identifier using the server-side bearer token");
  },
};
