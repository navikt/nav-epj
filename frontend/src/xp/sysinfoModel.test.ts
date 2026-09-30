import { describe, expect, it } from "vitest";
import { copy } from "./copy";
import {
  HPR_CLAIM,
  buildSummary,
  claimRows,
  fhirBaseOf,
  fhirRows,
  helseIdRows,
  resourceRows,
  smartRows,
} from "./sysinfoModel";
import type { Session, SmartConfiguration } from "../utils/mapping/epj";

const real: Session = {
  idp: "helseid",
  claims: {
    iss: "https://helseid-sts.test.nhn.no",
    aud: "nav-epj",
    name: "GRØNN VITS",
    [HPR_CLAIM]: "565501872",
  },
  issuedAt: "2026-09-28T06:58:00Z",
  expiresAt: "2026-09-28T07:58:00Z",
};

const local: Session = { idp: "local-stub", claims: { sub: "local-dev" } };

const user = { navn: "Bjarte Legesen", hpr: "111222333" };

const smart: SmartConfiguration = {
  issuer: "http://localhost:8080/oidc",
  jwks_uri: "http://localhost:8080/oidc/jwks",
  authorization_endpoint: "http://localhost:8080/oidc/authorize",
  token_endpoint: "http://localhost:8080/oidc/token",
  token_endpoint_auth_methods_supported: ["none", "client_secret_basic"],
  capabilities: ["launch-ehr", "context-ehr-patient", "context-ehr-encounter"],
};

describe("sysinfoModel", () => {
  it("describes a real session from its claims and token lifetime", () => {
    const now = new Date("2026-09-28T07:00:00Z");
    const rows = Object.fromEntries(helseIdRows(real, user, now));
    expect(rows[copy["s11.helseid.user"]]).toBe("GRØNN VITS");
    expect(rows[copy["s11.helseid.hpr"]]).toBe("565501872");
    expect(rows[copy["s11.helseid.idp"]]).toBe(copy["s11.helseid.idp.value"]);
    expect(rows[copy["s11.helseid.iat"]]).toMatch(/^28\.09\.2026 \d\d:\d\d$/);
    expect(rows[copy["s11.helseid.exp"]]).toMatch(
      /^28\.09\.2026 \d\d:\d\d \(om 58 min\)$/,
    );
  });

  it("marks an expired id token instead of counting down", () => {
    const now = new Date("2026-09-28T09:00:00Z");
    const rows = Object.fromEntries(helseIdRows(real, user, now));
    expect(rows[copy["s11.helseid.exp"]]).toMatch(
      /^28\.09\.2026 \d\d:\d\d \(utløpt\)$/,
    );
    expect(rows[copy["s11.helseid.exp"]]).not.toContain("om ");
  });

  it("treats the exact expiry instant as expired", () => {
    const now = new Date("2026-09-28T07:58:00Z");
    const rows = Object.fromEntries(helseIdRows(real, user, now));
    expect(rows[copy["s11.helseid.exp"]]).toContain("utløpt");
  });

  it("still counts down when less than a minute is left", () => {
    const now = new Date("2026-09-28T07:57:40Z");
    const rows = Object.fromEntries(helseIdRows(real, user, now));
    expect(rows[copy["s11.helseid.exp"]]).toMatch(/\(om 0 min\)$/);
  });

  it("describes the local stub with the logged in user and no token times", () => {
    const rows = Object.fromEntries(helseIdRows(local, user, new Date()));
    expect(rows[copy["s11.helseid.user"]]).toBe("Bjarte Legesen");
    expect(rows[copy["s11.helseid.hpr"]]).toBe("111222333");
    expect(rows[copy["s11.helseid.idp"]]).toBe(copy["s11.helseid.idp.local"]);
    expect(rows[copy["s11.helseid.iat"]]).toBe(copy["s4.empty.value"]);
    expect(rows[copy["s11.helseid.exp"]]).toBe(copy["s4.empty.value"]);
  });

  it("lists the claims in a fixed order with pid always hidden", () => {
    expect(claimRows(real)).toEqual([
      ["iss", "https://helseid-sts.test.nhn.no"],
      ["aud", "nav-epj"],
      ["name", "GRØNN VITS"],
      ["hpr_number", "565501872"],
      ["pid", copy["s11.claims.hidden"]],
      ["iat", "2026-09-28T06:58:00Z"],
      ["exp", "2026-09-28T07:58:00Z"],
    ]);
  });

  it("keeps unknown claims after the fixed ones", () => {
    expect(claimRows(local).at(-1)).toEqual(["sub", "local-dev"]);
    expect(claimRows(local)[0]).toEqual(["iss", copy["s4.empty.value"]]);
  });

  it("hides pid even if the server were to send it", () => {
    const leaked: Session = {
      ...real,
      claims: {
        ...real.claims,
        "helseid://claims/identity/pid": "01019012345",
      },
    };
    const rows = claimRows(leaked);
    expect(rows.filter(([claim]) => claim === "pid")).toEqual([
      ["pid", copy["s11.claims.hidden"]],
    ]);
    expect(JSON.stringify(rows)).not.toContain("01019012345");
  });

  it("takes the FHIR base from the server's own authorize endpoint, not the browser", () => {
    expect(fhirBaseOf(smart)).toBe("http://localhost:8080/fhir");
    expect(
      fhirBaseOf({
        ...smart,
        authorization_endpoint: "https://epj.test.nav.no/oidc/authorize",
      }),
    ).toBe("https://epj.test.nav.no/fhir");
  });

  it("degrades to dashes when the authorize endpoint is not an absolute URL", () => {
    const broken = { ...smart, authorization_endpoint: "/oidc/authorize" };
    expect(fhirBaseOf(broken)).toBeNull();
    const rows = Object.fromEntries(smartRows(broken));
    expect(rows[copy["s11.smart.iss"]]).toBe(copy["s4.empty.value"]);
    expect(rows[copy["s11.smart.discovery"]]).toBe(copy["s4.empty.value"]);
    expect(rows[copy["s11.smart.token"]]).toBe(smart.token_endpoint);
  });

  it("computes SMART endpoints from the discovery document", () => {
    expect(Object.fromEntries(smartRows(smart))).toEqual({
      [copy["s11.smart.iss"]]: "http://localhost:8080/fhir",
      [copy["s11.smart.discovery"]]:
        "http://localhost:8080/fhir/.well-known/smart-configuration",
      [copy["s11.smart.authorize"]]: "http://localhost:8080/oidc/authorize",
      [copy["s11.smart.token"]]: "http://localhost:8080/oidc/token",
      [copy["s11.smart.jwks"]]: "http://localhost:8080/oidc/jwks",
      [copy["s11.smart.launch"]]: copy["s11.smart.launch.value"],
      [copy["s11.smart.clientAuth"]]: "none, client_secret_basic",
      [copy["s11.smart.accessToken"]]: copy["s11.smart.accessToken.value"],
      [copy["s11.smart.context"]]: "context-ehr-patient, context-ehr-encounter",
    });
  });

  it("shows a dash when no token context is advertised", () => {
    const rows = Object.fromEntries(
      smartRows({ ...smart, capabilities: ["launch-ehr"] }),
    );
    expect(rows[copy["s11.smart.context"]]).toBe(copy["s4.empty.value"]);
  });

  it("reads version, capability URL and resources from the CapabilityStatement", () => {
    const statement = {
      fhirVersion: "4.0.1",
      rest: [
        {
          resource: [
            {
              type: "Patient",
              interaction: [{ code: "read" }, { code: "search-type" }],
            },
            { type: "Encounter" },
          ],
        },
      ],
    };
    expect(fhirRows(statement, "https://x/fhir")).toEqual([
      [copy["s11.fhir.version"], "4.0.1 (R4)"],
      [copy["s11.fhir.capability"], "https://x/fhir/metadata"],
    ]);
    expect(resourceRows(statement)).toEqual([
      ["Patient", "read, search-type"],
      ["Encounter", copy["s4.empty.value"]],
    ]);
  });

  it("shows a dash for the CapabilityStatement URL while the base is unknown", () => {
    expect(fhirRows({ fhirVersion: "4.0.1" }, null)[1]).toEqual([
      copy["s11.fhir.capability"],
      copy["s4.empty.value"],
    ]);
  });

  it("shows other FHIR versions as reported", () => {
    expect(fhirRows({ fhirVersion: "5.0.0" }, "https://x/fhir")[0][1]).toBe(
      "5.0.0",
    );
    expect(resourceRows({ fhirVersion: "5.0.0" })).toEqual([]);
  });

  it("builds a plain text summary", () => {
    expect(
      buildSummary([
        {
          title: "A",
          rows: [
            ["x", "1"],
            ["y", "2"],
          ],
        },
        { title: "B", rows: [["z", "3"]] },
      ]),
    ).toBe(`${copy["s11.title"]}\n\nA\nx: 1\ny: 2\n\nB\nz: 3`);
  });
});
