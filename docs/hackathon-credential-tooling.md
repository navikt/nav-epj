# Hackathon team credential tooling

`./gradlew generateHackathonCredentials` is a local operator command for the hackathon only. It reads a non-secret manifest
and writes secret-backed credentials for 1-10 teams into a new directory outside the checkout. It
is not a server endpoint and does not deploy anything.

Each team gets four registrations: public, `client_secret_basic`, `private_key_jwt` launch, and
Backend Services. Client secrets are 32 random bytes. The two asymmetric clients have independent
RSA 3072-bit RS384 keys and key IDs. Every registration is validated by the normal registry loader
before anything is written.

```bash
./gradlew generateHackathonCredentials \
  -PhackathonManifest=/path/to/manifest.json \
  -PhackathonOutput=/path/outside/checkout/team-credentials \
  -PhackathonClinicians=/path/to/clinicians.json \
  -PhackathonRoster=/path/to/roster.json
```

Pass `-PhackathonCredentialsOnly=true` instead of the last two properties to produce incomplete
packets when the clinician logins and roster are not available yet. Passing only one of the two
inputs, or neither without `hackathonCredentialsOnly`, is refused, so a missing input is never
silent. Only paths are given as properties; no secret is passed on a command line.

## Manifest (non-secret)

```json
{
  "schemaVersion": 1,
  "interactiveScopes": ["openid", "launch", "patient/Patient.rs"],
  "systemScopes": ["system/Patient.rs"],
  "teams": [
    {
      "teamSlot": "alpha",
      "publicClient": {"launchUri": "https://...", "callbackUri": "https://..."},
      "clientSecretClient": {"launchUri": "https://...", "callbackUri": "https://..."},
      "privateKeyJwtClient": {"launchUri": "https://...", "callbackUri": "https://..."}
    }
  ]
}
```

URLs are exact; the tool assumes no callback paths or ports. `teamSlot` is 1-64 letters, digits,
underscores or hyphens, starting with a letter or digit, and must be unique ignoring case.

## Separate sensitive inputs

The track team supplies the clinician logins and the cohort provisioner supplies the roster. Their
content is copied into packets verbatim as flat string fields; the tool invents no fields.

```json
{"schemaVersion": 1, "teams": [{"teamSlot": "alpha", "clinician": {"<field>": "<value>"}}]}
```

```json
{"schemaVersion": 1, "patients": [{"<field>": "<value>"}]}
```

The clinician file must have exactly one entry per manifest team. The roster is shared by all
teams. Limits: 20 fields per object, 200 patients, 2048 characters per value, 1 MiB per file.

## Output

```
<output>/                         mode 700
  registry.json                   mode 600, whole registry for smart.clientRegistryJson
  teams/<teamSlot>/               mode 700
    packet.json                   mode 600, schemaVersion 1
    launch-key.private.jwk.json   mode 600, private key of the private_key_jwt launch client
    backend-key.private.jwk.json  mode 600, private key of the Backend Services client
```

`registry.json` holds public JWKs and the shared client secrets, never private keys or clinician
data, and is sensitive. Give a team only its own `teams/<teamSlot>/` directory.

`packet.json` carries `status` (`COMPLETE` or `CREDENTIALS_ONLY`), `missingInputs` (empty, or
`clinician` and `roster`), `teamSlot`, `clients` (`public`, `clientSecret`, `privateKeyJwt`,
`backendServices` with client IDs, secret or key ID, algorithm and key file name, plus launch and
callback URLs for the interactive variants), `scopes`, and, for complete packets only,
`clinician` and `roster`.

## Adding or rotating teams in a deployed registry

The output never overwrites a deployed registry. To change a registry that is already deployed,
export the current `smart.clientRegistryJson` secret to a file (it is sensitive) and pass it with
`-PhackathonExistingRegistry=/path/to/current-registry.json`. The tool validates it, keeps every
registration it does not touch unchanged (including clients without a `teamSlot`), and writes the
merged result to the new `registry.json`.

- Add teams: the manifest lists only the new teams. A team that is already registered
  (compared ignoring case) is refused, and the registry may not exceed 10 teams.
- Rotate teams: add `-PhackathonRotate=true` and list only the teams to rotate, with their exact
  URLs. Each must already have exactly its four generated registrations (public,
  `client_secret_basic`, `private_key_jwt` launch, `client_credentials`); unknown teams and
  partially registered teams are refused. The four registrations are replaced with new client
  IDs, secret and keys.

Clinician and roster inputs must cover exactly the teams in the manifest, and packets are written
only for those teams. After reviewing the output, store the new `registry.json` in the secret
store and redeploy, since the registry is loaded at startup. Rotated credentials stop working only
once the new registry is live, so hand out the new packet after that.

## Safety rules

- The output path must be new: existing files, directories and symlinks are never overwritten, the
  parent must exist, and any path that resolves inside the checkout (including through symlinks)
  is refused.
- Directories and files are created with owner-only permissions from the start. Platforms without
  POSIX permissions are refused.
- Inputs are validated before keys are generated or anything is written. If a write fails, only
  paths created by that run are removed.
- Errors name the input and field but never print file contents; secrets are not printed to
  stdout or stderr.
- Deployment is separate: copy `registry.json` into the secret store yourself. This tooling does
  not implement the starter apps.

## Deletion boundary

This tooling is hackathon-specific and is meant to be deleted afterwards. It is self-contained in:

- `src/main/kotlin/no/nav/helse/hackathon/credentials/` and the matching test directory
- the `generateHackathonCredentials` task in `build.gradle.kts`
- this document and its link in `smart-hackathon-contract.md`

It is not wired into the running application and adds no dependencies. The core SMART registry
(`smart.security`, including `RawClientRegistration` and the registry validators) does not
reference it, and the 10-team cap exists only in this package. Deleting the items above leaves the
EHR unaffected. Generated credential files are operator artifacts, never application resources.
