import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { copy } from "./copy";
import { renderApp, setupBrowserStubs, stubApi, testSessionOrg } from "./testApp";

const fjordvik = { id: "a", navn: "Fjordvik Legekontor", orgnummer: "311111116", tlf: null };
const lyngdal = { id: "b", navn: "Lyngdal Legesenter", orgnummer: "311111124", tlf: null };
const choosable = [
  { org: fjordvik, hasOwnList: false },
  { org: lyngdal, hasOwnList: false },
];
const unresolved: unknown = { org: null, needsChoice: true, canChoose: true, choosableOrgs: choosable };
const resolved = { org: fjordvik, needsChoice: false, canChoose: true, choosableOrgs: choosable };

beforeEach(setupBrowserStubs);
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("session org", () => {
  it("asks for an org after login and lands on the patient list after choosing", async () => {
    const user = userEvent.setup();
    let current: unknown = unresolved;
    const calls = stubApi({
      "GET /api/session-org": () => ({ body: current }),
      "PUT /api/session-org": () => {
        current = resolved;
        return { body: resolved };
      },
      "GET /api/patient": () => ({ body: [] }),
      "GET /api/active-patient": () => ({ status: 204, body: null }),
    });
    renderApp("/patients");

    expect(
      await screen.findByRole("heading", { name: copy["org.pick.title"] }),
    ).toBeInTheDocument();
    expect(calls.some((c) => c.key === "GET /api/patient")).toBe(false);
    expect(
      screen.queryByRole("button", { name: copy["header.changeOrg"] }),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Lyngdal Legesenter/ }));

    await waitFor(() =>
      expect(
        screen.queryByRole("heading", { name: copy["org.pick.title"] }),
      ).not.toBeInTheDocument(),
    );
    const put = calls.find((c) => c.key === "PUT /api/session-org");
    expect(JSON.parse(String(put?.init?.body))).toEqual({ orgnummer: "311111124" });
    expect(screen.getByRole("button", { name: copy["header.changeOrg"] })).toBeInTheDocument();
  });

  it("does not offer a change when the org is derived", async () => {
    stubApi({
      "GET /api/session-org": () => ({ body: testSessionOrg }),
      "GET /api/patient": () => ({ body: [] }),
    });
    renderApp("/patients");
    await screen.findByText(/Storgata legekontor/);
    expect(
      screen.queryByRole("button", { name: copy["header.changeOrg"] }),
    ).not.toBeInTheDocument();
  });

  it("offers Bytt legekontor when the org is chosen", async () => {
    const user = userEvent.setup();
    stubApi({
      "GET /api/session-org": () => ({ body: resolved }),
      "GET /api/patient": () => ({ body: [] }),
    });
    renderApp("/patients");
    await user.click(await screen.findByRole("button", { name: copy["header.changeOrg"] }));
    expect(
      await screen.findByRole("heading", { name: copy["org.pick.title"] }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: copy["org.pick.cancel"] }));
    expect(
      screen.queryByRole("heading", { name: copy["org.pick.title"] }),
    ).not.toBeInTheDocument();
  });
});
