import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SysinfoPage } from "./SysinfoPage";
import { expectNoSeriousViolations } from "./axeHelper";
import { useBalloonStore } from "./balloonStore";
import { copy } from "./copy";
import { CurrentUserContext } from "./currentUser";

const PID = "01019012345";

const realSession = {
  idp: "helseid",
  claims: {
    iss: "https://helseid-sts.test.nhn.no",
    aud: "nav-epj",
    name: "GRØNN VITS",
    "helseid://claims/hpr/hpr_number": "565501872",
  },
  issuedAt: "2026-09-28T06:58:00Z",
  expiresAt: "2026-09-28T07:58:00Z",
};

const localSession = { idp: "local-stub", claims: { sub: "local-dev" } };

const smartConfiguration = {
  issuer: "http://localhost:8080/oidc",
  jwks_uri: "http://localhost:8080/oidc/jwks",
  authorization_endpoint: "http://localhost:8080/oidc/authorize",
  token_endpoint: "http://localhost:8080/oidc/token",
  token_endpoint_auth_methods_supported: ["none", "client_secret_basic"],
  capabilities: ["launch-ehr", "context-ehr-patient", "context-ehr-encounter"],
};

const capabilityStatement = {
  resourceType: "CapabilityStatement",
  fhirVersion: "4.0.1",
  rest: [
    {
      mode: "server",
      resource: [
        {
          type: "Patient",
          interaction: [{ code: "read" }, { code: "search-type" }],
        },
        { type: "Encounter", interaction: [{ code: "read" }] },
      ],
    },
  ],
};

type Responses = Record<string, { status?: number; body: unknown }>;

function stubFetch(overrides: Responses = {}) {
  const responses: Responses = {
    "/api/session": { body: realSession },
    "/fhir/.well-known/smart-configuration": { body: smartConfiguration },
    "/fhir/metadata": { body: capabilityStatement },
    ...overrides,
  };
  const fn = vi.fn(async (url: string) => {
    const { status = 200, body } = responses[url] ?? { status: 404, body: {} };
    return { ok: status < 400, status, json: async () => body };
  });
  vi.stubGlobal("fetch", fn);
  return fn;
}

function renderPage() {
  return render(
    <CurrentUserContext.Provider
      value={{
        navn: "Bjarte Legesen",
        hpr: "111222333",
        autorisasjon: "Lege",
        legekontor: "Kontor",
      }}
    >
      <SysinfoPage />
    </CurrentUserContext.Provider>,
  );
}

async function ready() {
  await screen.findByText("4.0.1 (R4)");
}

describe("SysinfoPage", () => {
  beforeEach(() => stubFetch());
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("has the page heading and a copy button", async () => {
    renderPage();
    await ready();
    expect(
      screen.getByRole("heading", { level: 1, name: copy["s11.title"] }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: copy["s11.copy"] }),
    ).toBeInTheDocument();
  });

  it("fetches everything from the server instead of hard-coding it", async () => {
    const fetchMock = stubFetch();
    renderPage();
    await ready();
    expect(fetchMock.mock.calls.map(([url]) => url).sort()).toEqual([
      "/api/session",
      "/fhir/.well-known/smart-configuration",
      "/fhir/metadata",
    ]);
  });

  it("shows an active HelseID session from GET /api/session", async () => {
    vi.useFakeTimers({
      toFake: ["Date"],
      now: new Date("2026-09-28T07:00:00Z"),
    });
    renderPage();
    await ready();
    const section = within(
      screen.getByRole("region", { name: copy["s11.helseid.title"] }),
    );
    expect(section.getByText(copy["s11.helseid.active"])).toBeInTheDocument();
    expect(
      section.getByText(copy["s11.helseid.user"]).nextElementSibling,
    ).toHaveTextContent("GRØNN VITS");
    expect(
      section.getByText(copy["s11.helseid.hpr"]).nextElementSibling,
    ).toHaveTextContent("565501872");
    expect(
      section.getByText(copy["s11.helseid.idp"]).nextElementSibling,
    ).toHaveTextContent(copy["s11.helseid.idp.value"]);
    expect(
      section.getByText(copy["s11.helseid.exp"]).nextElementSibling,
    ).toHaveTextContent(/\(om 58 min\)$/);
  });

  it("marks an expired id token as expired", async () => {
    vi.useFakeTimers({
      toFake: ["Date"],
      now: new Date("2026-09-28T09:00:00Z"),
    });
    renderPage();
    await ready();
    const section = within(
      screen.getByRole("region", { name: copy["s11.helseid.title"] }),
    );
    const value = section.getByText(copy["s11.helseid.exp"]).nextElementSibling;
    expect(value).toHaveTextContent(copy["s5.status.session"]);
    expect(value).not.toHaveTextContent("om ");
  });

  it("lists the claims with pid hidden and never shows a token", async () => {
    renderPage();
    await ready();
    const table = screen.getByRole("table", { name: copy["s11.claims.title"] });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(
      rows.map((r) => within(r).getAllByRole("cell")[0].textContent),
    ).toEqual(["iss", "aud", "name", "hpr_number", "pid", "iat", "exp"]);
    expect(
      within(rows[4]).getByText(copy["s11.claims.hidden"]),
    ).toBeInTheDocument();
    expect(screen.getByText(copy["s11.claims.note"])).toBeInTheDocument();
    expect(document.body.textContent).not.toContain(PID);
    expect(document.body.textContent).not.toMatch(/eyJ/);
  });

  it("marks local development and falls back to the logged in user", async () => {
    stubFetch({ "/api/session": { body: localSession } });
    renderPage();
    await ready();
    const section = within(
      screen.getByRole("region", { name: copy["s11.helseid.title"] }),
    );
    expect(section.getByText(copy["s11.helseid.local"])).toBeInTheDocument();
    expect(
      section.getByText(copy["s11.helseid.idp.local"]),
    ).toBeInTheDocument();
    expect(section.getByText("Bjarte Legesen")).toBeInTheDocument();
    expect(section.getByText("local-dev")).toBeInTheDocument();
  });

  it("computes the SMART endpoints from the discovery document", async () => {
    renderPage();
    await ready();
    const section = within(
      screen.getByRole("region", { name: copy["s11.smart.title"] }),
    );
    const base = "http://localhost:8080/fhir";
    expect(
      section.getByText(copy["s11.smart.iss"]).nextElementSibling,
    ).toHaveTextContent(base);
    expect(
      section.getByText(copy["s11.smart.discovery"]).nextElementSibling,
    ).toHaveTextContent(`${base}/.well-known/smart-configuration`);
    expect(
      section.getByText(copy["s11.smart.authorize"]).nextElementSibling,
    ).toHaveTextContent("http://localhost:8080/oidc/authorize");
    expect(
      section.getByText(copy["s11.smart.token"]).nextElementSibling,
    ).toHaveTextContent("http://localhost:8080/oidc/token");
    expect(
      section.getByText(copy["s11.smart.jwks"]).nextElementSibling,
    ).toHaveTextContent("http://localhost:8080/oidc/jwks");
    expect(
      section.getByText(copy["s11.smart.clientAuth"]).nextElementSibling,
    ).toHaveTextContent("none, client_secret_basic");
    expect(
      section.getByText(copy["s11.smart.context"]).nextElementSibling,
    ).toHaveTextContent("context-ehr-patient, context-ehr-encounter");
    expect(section.getByText(copy["s11.smart.validator"])).toBeInTheDocument();
  });

  it("shows the FHIR version, the CapabilityStatement and a resource table", async () => {
    renderPage();
    await ready();
    const section = within(
      screen.getByRole("region", { name: copy["s11.fhir.title"] }),
    );
    expect(
      section.getByText(copy["s11.fhir.capability"]).nextElementSibling,
    ).toHaveTextContent("http://localhost:8080/fhir/metadata");
    const table = section.getByRole("table");
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows.map((r) => r.textContent)).toEqual([
      "Patientread, search-type",
      "Encounterread",
    ]);
  });

  it("does not include a conformance checklist", async () => {
    renderPage();
    await ready();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("shows one failing section with a retry while the others still load", async () => {
    const fetchMock = stubFetch({
      "/fhir/metadata": { status: 500, body: {} },
    });
    renderPage();
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(copy["s8.NETWORK.head"]);
    expect(
      within(
        screen.getByRole("region", { name: copy["s11.fhir.title"] }),
      ).getByRole("alert"),
    ).toBe(alert);
    expect(screen.getByText(copy["s11.helseid.active"])).toBeInTheDocument();
    expect(screen.getByText(copy["s11.smart.authorize"])).toBeInTheDocument();
    fetchMock.mockClear();
    stubFetch();
    await userEvent.click(
      screen.getByRole("button", { name: copy["s8.NETWORK.action"] }),
    );
    await ready();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("copies a plain text summary without the pid and confirms it", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("navigator", { clipboard: { writeText } });
    renderPage();
    await ready();
    await userEvent.click(
      screen.getByRole("button", { name: copy["s11.copy"] }),
    );
    await vi.waitFor(() => expect(writeText).toHaveBeenCalledOnce());
    const text = String(writeText.mock.calls[0][0]);
    expect(text).toContain(copy["s11.title"]);
    expect(text).toContain(`${copy["s11.helseid.hpr"]}: 565501872`);
    expect(text).toContain("pid: skjult");
    expect(text).toContain("Patient: read, search-type");
    expect(text).not.toContain(PID);
    await vi.waitFor(() =>
      expect(useBalloonStore.getState().balloon?.title).toBe(
        copy["s11.copied"],
      ),
    );
  });

  it("has no serious accessibility violations", async () => {
    const { container } = renderPage();
    await ready();
    await expectNoSeriousViolations(container);
  });
});
