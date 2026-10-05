# SMART client authentication

`nav-epj` is the authorization server for SMART on FHIR launch and Backend Services flows. Every
registered client authenticates itself at the token endpoint (`POST /oidc/token`) using one of the
methods listed in `token_endpoint_auth_methods_supported` in the discovery document
(`GET /fhir/.well-known/smart-configuration`).

## Supported methods

| `token_endpoint_auth_methods_supported` | Client config                        | Verification                                                                                                            |
|-----------------------------------------|--------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `none`                                  | no `clientSecret` / `jwksUri` needed | public client, no authentication (PKCE only)                                                                            |
| `client_secret_basic`                   | `clientSecret`                       | HTTP Basic auth, constant-time comparison                                                                               |
| `private_key_jwt`                       | `jwksUri` or `jwkSet`                | Signed JWT assertion, verified against the client's published JWKS or inline JWK Set (`client-confidential-asymmetric`) |

### Unsupported method

`client_secret_post` is not implemented.

## Backend Services clients

Backend Services clients use only the `client_credentials` grant and authenticate with
`private_key_jwt`. They have no redirect or launch URIs and may register only system scopes. The
supported system scopes are listed by the discovery document; requested scopes must be a subset
of those registered for the client.

```yaml
smart:
  clients:
    - clientId: "team-01-backend"
      tokenEndpointAuthMethod: "private_key_jwt"
      jwksUri: "https://backend.example.com/jwks.json"
      grantTypes: [ "client_credentials" ]
      scopes:
        - "system/Patient.rs"
        - "system/Encounter.rs"
        - "system/Condition.s"
        - "system/Observation.crs"
        - "system/Practitioner.r"
        - "system/PractitionerRole.s"
        - "system/Organization.r"
        - "system/DocumentReference.crs"
```

Request a token with `grant_type=client_credentials`, the registered `client_id`, the requested
`scope`, and a signed `client_assertion`. The assertion uses the same claims and verification
rules described below. The issued access token has no patient or encounter context, expires within
five minutes, and has no refresh token. FHIR searches still require the server's supported search
criteria; for example, `Patient` search requires `identifier`.

## Registering a client for `private_key_jwt`

A `private_key_jwt` client registers exactly one of:

- `jwksUri`: a remote, unauthenticated HTTPS endpoint serving the client's public JWK Set. Verified
  by fetching it (with caching) at token-exchange time.
- `jwkSet`: the client's public JWK Set inlined directly in the registration. Verified with zero
  network fetch. This is the only option for a client running on `localhost`, since a deployed
  `nav-epj` can never reach a participant's own machine.

```yaml
smart:
  clients:
    - clientId: "my-app"
      redirectUris: [ "https://my-app.example.com/fhir/callback" ]
      launchUris: [ "https://my-app.example.com/fhir/launch" ]
      tokenEndpointAuthMethod: "private_key_jwt"
      jwksUri: "https://my-app.example.com/fhir/jwks.json"
```

`jwksUri` must point to an open (unauthenticated) endpoint serving the client's JWK set (public
keys)

Inline registration (`jwkSet`) for a localhost client:

```yaml
smart:
  clients:
    - clientId: "team-01-private-key-jwt"
      redirectUris: [ "http://localhost:3000/callback" ]
      launchUris: [ "http://localhost:3000/launch" ]
      tokenEndpointAuthMethod: "private_key_jwt"
      jwkSet: |
        {"keys":[{"kty":"EC","crv":"P-384","kid":"team-01-key-1","use":"sig","alg":"ES384","x":"...","y":"..."}]}
```

`jwkSet` must contain **public** key material only. Registration fails startup if any key is
private, has an unsupported type/curve (only RSA and EC P-384 are accepted, matching
`token_endpoint_auth_signing_alg_values_supported`), declares a `use` other than `sig`, declares
encryption/derivation `key_ops`, is missing a unique `kid`, or declares an `alg` outside `RS384`/
`ES384`. A client may configure `jwksUri` or `jwkSet`, never both.

Client implementation requirements:

1. Generate an asymmetric key pair (RSA or EC) and either publish the public key at `jwksUri` as a
   JWK Set (`{"keys": [...]}`) or configure it inline under `jwkSet`
2. Sign the client assertion with `RS384` or `ES384`
   (see [/fhir/.well-known/smart-configuration](../src/main/kotlin/no/nav/helse/smart/api/SmartRouting.kt)
   `token_endpoint_auth_signing_alg_values_supported`)
3. Set the JWS header:
    1. `alg`: `RS384` or `ES384`
    2. `typ`: JWT
    3. `kid`: matching the `kid` of the key published at `jwksUri` or registered in `jwkSet`
4. Set the JWT claims:
    1. `iss` and `sub`: the client's `clientId`
    2. `aud`: the token endpoint URL (`{issuerBaseUrl}/token`)
    3. `exp`: expiry no more than 5 minutes in the future
    4. `jti`: a unique value per assertion
5. Send the token request with:
    1. `client_assertion_type`: `urn:ietf:params:oauth:client-assertion-type:jwt-bearer`
    2. `client_assertion`: the signed JWT

Code example using TypeScript and `jose`:

```typescript
import {exportJWK, generateKeyPair, SignJWT} from "jose";

const clientId = "my-app";
const redirectUri = "https://my-app.example.com/fhir/callback";
const tokenEndpoint = "https://<issuer>/oidc/token"; // from /.well-known/smart-configuration

const {privateKey, publicKey} = await generateKeyPair("ES384", {extractable: true});
const kid = "my-app-key-1";
const publicJwk = {...(await exportJWK(publicKey)), kid, alg: "ES384", use: "sig"}; // publish this at jwksUri (public keys only)

async function createClientAssertion(clientId: string, tokenEndpoint: string) {
  return new SignJWT({})
    .setProtectedHeader({alg: "ES384", typ: "JWT", kid})
    .setIssuer(clientId)
    .setSubject(clientId)
    .setAudience(tokenEndpoint)
    .setExpirationTime("5m")
    .setJti(crypto.randomUUID())
    .sign(privateKey);
}

const codeVerifier = crypto.getRandomValues(new Uint8Array(32)); // any cryptographically random string 43-128 chars
const codeChallenge = await sha256Base64Url(codeVerifier);

const body = new URLSearchParams({
  grant_type: "authorization_code",
  code,
  code_verifier: codeVerifier,
  redirect_uri: redirectUri,
  client_id: clientId,
  client_assertion_type: "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
  client_assertion: await createClientAssertion(clientId, tokenEndpoint)
});

await fetch(tokenEndpoint, {
  method: "POST",
  headers: {"Content-Type": "application/x-www-form-urlencoded"},
  body
});
```

Failure modes

Any verification failure (unknown `kid`, wrong algorithm, expired/too-long-lived assertion, `jku`/
`iss`/`sub` mismatch, reused `jti`, etc) results in a `401 Unauthorized` with
`error=invalid_client`, and does NOT consume the authorization code. The client may retry with a
corrected assertion.

For an inline-registered client (`jwkSet`), a `jku` header is always rejected outright: there is no
registered `jwksUri` to match it against, so trusting a client-supplied key-set URL would defeat
the point of inlining the key.

## Client registry sources

Registrations are typed and validated once at startup (duplicate `clientId`, incompatible or
missing auth material, unsupported auth method/algorithm, insecure or wildcard redirect/launch
URIs, and invalid `jwkSet` content all fail startup with an explicit error). Two sources are
supported, and only one is read per environment:

- `smart.clientRegistryJson`: a secret-backed JSON array of the same registration shape, used in
  deployed environments. Never commit real values for this; it is sourced from a Kubernetes secret.
- `smart.clients`: a plain YAML list, used for local development (`application-local.yaml`).

Both sources produce the same typed `SmartClient` registry; there is no behavioral difference
between a client registered via YAML and one registered via the JSON document.