# SMART client authentication

`nav-epj` is the authorization server for the SMART on FHIR launch flow. Every registered client
authenticates itself at the token endpoint (`POST /oidc/token`) using one of the methods listed in
`tokenEndpointAuthMethodsSupported` in the discovery document
(`GET /fhir/.well-known/smart-configuration`).

## Supported methods

| `tokenEndpointAuthMethodsSupported` | Client config                        | Verification                                                                                          |
|-------------------------------------|--------------------------------------|-------------------------------------------------------------------------------------------------------|
| `none`                              | no `clientSecret` / `jwksUri` needed | public client, no authentication (PKCE only)                                                          |
| `client_secret_basic`               | `clientSecret`                       | HTTP Basic auth, constant-time comparison                                                             |
| `private_key_jwt`                   | `jwksUri`                            | Signed JWT assertion, verified against the client's published JWKS (`client-confidential-assymetric`) |

### Future supported methods

`client_credentials` and `client_secret_post` auth are not implemented yet.

## Registering a client for `private_key_jwt`

```yaml
smart:
  clients:
    - clientId: "my-app"
      redirectUris: [ "https://my-app.example.com/fhir/callback" ]
      launchUris: [ "https://my-app.example.com/fhir/launch" ]
      tokenEndpointAuthMethod: [ "private_key_jwt" ]
      jwksUri: "https://my-app.example.com/fhir/jwks.json"
```

`jwksUri` must point to an open (unauthenticated) endpoint serving the client's JWK set (public
keys)

Client implementation requirements:

1. Generate an asymmetric key pair (RSA or EC) and publish the public key at `jwksUri` as a JWK Set
   (`{"keys": [...]}`)
2. Sign the client assertion with `RS384` or `ES384`
   (see [/fhir/.well-known/smart-configuration](../src/main/kotlin/no/nav/helse/smart/api/SmartRouting.kt)
   `tokenEndpointAuthSigningAlgValuesSupported`)
3. Set the JWS header:
    1. `alg`: `RS384` or `ES384`
    2. `typ`: JWT
    3. `kid`: matching the `kid` of the key published at `jwksUri`
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