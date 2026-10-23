import { unfinished } from "./config.ts";
import type * as reference from "./reference.ts";

export type Steps = Pick<typeof reference,
  "readLaunch" | "discover" | "pkce" | "newTransaction" | "authorizationUrl" |
  "validateCallback" | "exchange" | "verifyIdentity" | "launchContext" | "loadChart">;

export const steps: Steps = {
  readLaunch(query, config) {
    return unfinished(1, "Require a launch ID and an iss that equals FHIR_BASE_URL");
  },
  async discover(config) {
    return unfinished(2, "Fetch and validate SMART discovery; never trust arbitrary endpoint URLs");
  },
  pkce() {
    return unfinished(3, "Generate a random PKCE verifier and its base64url SHA-256 challenge");
  },
  newTransaction(launch, discovery, proof) {
    return unfinished(4, "Create random state and a server-side launch transaction");
  },
  authorizationUrl(config, transaction) {
    return unfinished(5, "Build the authorization URL with scopes, aud, state and PKCE");
  },
  validateCallback(query, transaction) {
    return unfinished(6, "Check state and transaction age before accepting the code");
  },
  async exchange(config, transaction, code) {
    return unfinished(7, "Exchange the code server-side using this variant's client authentication");
  },
  async verifyIdentity(config, discovery, tokens) {
    return unfinished(8, "Verify ID-token signature, issuer, audience and expiry before reading fhirUser");
  },
  launchContext(tokens, practitioner) {
    return unfinished(9, "Read patient and encounter from the token response (beside access_token) and fhirUser from the verified ID token");
  },
  async loadChart(config, tokens, context) {
    return unfinished(10, "Fetch patient and active encounter for rendering; care-team details are optional");
  },
};
