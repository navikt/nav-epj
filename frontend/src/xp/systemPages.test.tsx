import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { sykInn, validator } from "./appFixtures";
import { renderApp, setupBrowserStubs, stubApi, testKontor } from "./testApp";

const session = {
  idp: "helseid",
  claims: {
    iss: "https://helseid-sts.test.nhn.no",
    aud: "nav-epj",
    name: "Kari Nordmann",
    "helseid://claims/hpr/hpr_number": "9144889",
  },
  issuedAt: "2026-09-28T06:58:00Z",
  expiresAt: "2026-09-28T07:58:00Z",
};

function stubAll() {
  return stubApi({
    "GET /api/apps": () => ({
      body: [sykInn, { ...validator, launchMode: "tab" }],
    }),
    "GET /api/legekontor/k1": () => ({
      body: { ...testKontor, tlf: "22 33 44 55" },
    }),
    "GET /api/session": () => ({ body: session }),
    "GET /fhir/.well-known/smart-configuration": () => ({
      body: {
        issuer: "http://localhost:8080/oidc",
        jwks_uri: "http://localhost:8080/oidc/jwks",
        authorization_endpoint: "http://localhost:8080/oidc/authorize",
        token_endpoint: "http://localhost:8080/oidc/token",
        token_endpoint_auth_methods_supported: ["none"],
        capabilities: ["context-ehr-patient"],
      },
    }),
    "GET /fhir/metadata": () => ({
      body: {
        fhirVersion: "4.0.1",
        rest: [
          { resource: [{ type: "Patient", interaction: [{ code: "read" }] }] },
        ],
      },
    }),
  });
}

async function openSystemPage(label: string) {
  const pane = await screen.findByRole("navigation", {
    name: copy["nav.label"],
  });
  await userEvent.click(within(pane).getByRole("button", { name: label }));
}

describe("system pages in the shell", () => {
  beforeEach(() => {
    setupBrowserStubs();
    stubAll();
  });

  it("opens Kontrollpanel as a document tab and lists the registered apps", async () => {
    renderApp("/");
    await openSystemPage(copy["pane.system.kontroll"]);
    expect(
      await screen.findByRole("heading", { level: 1, name: copy["s9.title"] }),
    ).toBeVisible();
    expect(
      screen.getByRole("tab", { name: copy["pane.system.kontroll"] }),
    ).toHaveAttribute("aria-selected", "true");
    await userEvent.click(
      screen.getByRole("button", { name: new RegExp(copy["s9.cat.apps"]) }),
    );
    const table = await screen.findByRole("table", {
      name: copy["s9.cat.apps"],
    });
    await waitFor(() =>
      expect(within(table).getAllByRole("row")).toHaveLength(3),
    );
    expect(within(table).getByText("syk-inn")).toBeInTheDocument();
    expect(
      within(table).getByText(copy["pane.apps.mode.tab"]),
    ).toBeInTheDocument();
  });

  it("shows the office and user from the existing helsepersonell fetch", async () => {
    renderApp("/");
    await openSystemPage(copy["pane.system.kontroll"]);
    await userEvent.click(
      await screen.findByRole("button", {
        name: new RegExp(copy["s9.cat.org"]),
      }),
    );
    const office = within(
      await screen.findByRole("region", { name: copy["s9.org.office"] }),
    );
    expect(office.getByText("Storgata legekontor")).toBeInTheDocument();
    expect(office.getByText("22 33 44 55")).toBeInTheDocument();
    const person = within(
      screen.getByRole("region", { name: copy["s9.org.user"] }),
    );
    expect(person.getByText("Kari Nordmann")).toBeInTheDocument();
    expect(person.getByText("9144889")).toBeInTheDocument();
    expect(person.getByText("Lege")).toBeInTheDocument();
  });

  it("has no theme controls left in the task pane", async () => {
    renderApp("/");
    const pane = await screen.findByRole("navigation", {
      name: copy["nav.label"],
    });
    expect(within(pane).queryByRole("radio")).not.toBeInTheDocument();
    expect(within(pane).queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("opens Systeminformasjon and fills it from the three endpoints", async () => {
    const { container } = renderApp("/");
    await openSystemPage(copy["pane.system.sysinfo"]);
    expect(
      await screen.findByRole("heading", { level: 1, name: copy["s11.title"] }),
    ).toBeVisible();
    expect(await screen.findByText("4.0.1 (R4)")).toBeInTheDocument();
    expect(screen.getByText(copy["s11.helseid.active"])).toBeInTheDocument();
    expect(
      screen.getByText("http://localhost:8080/oidc/token"),
    ).toBeInTheDocument();
    await expectNoSeriousViolations(container);
  });

  it("opens Hjelp and returns to the start page when its tab is closed", async () => {
    renderApp("/");
    await openSystemPage(copy["pane.system.hjelp"]);
    expect(
      await screen.findByRole("heading", { level: 1, name: copy["s13.title"] }),
    ).toBeVisible();
    await userEvent.keyboard("{Control>}w{/Control}");
    await waitFor(() =>
      expect(
        screen.queryByRole("heading", { level: 1, name: copy["s13.title"] }),
      ).not.toBeInTheDocument(),
    );
    expect(
      screen.getByRole("tab", { name: copy["tabs.start"] }),
    ).toHaveAttribute("aria-selected", "true");
  });

  it("keeps the page when another tab is opened and closed", async () => {
    vi.stubGlobal("scrollTo", vi.fn());
    renderApp("/");
    await openSystemPage(copy["pane.system.hjelp"]);
    await openSystemPage(copy["pane.system.kontroll"]);
    await userEvent.click(
      screen.getByRole("tab", { name: copy["pane.system.hjelp"] }),
    );
    expect(
      screen.getByRole("heading", { level: 1, name: copy["s13.title"] }),
    ).toBeVisible();
  });
});
