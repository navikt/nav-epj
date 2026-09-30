import { cleanup, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderApp, setupBrowserStubs, stubApi } from "./testApp";

const patients = [
  {
    id: "p1",
    fornavn: "Matematisk",
    etternavn: "Ape",
    personident: "01019012345",
    personidentType: "FNR",
    birthDate: "1990-01-01",
    gender: "MALE",
  },
  {
    id: "p2",
    fornavn: "Ola",
    etternavn: "Nordmann",
    personident: "02029054321",
    personidentType: "FNR",
    birthDate: "1990-02-02",
    gender: "MALE",
  },
];

beforeEach(setupBrowserStubs);
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("/patients route", () => {
  it("lists the patients from the API", async () => {
    stubApi({ "GET /api/patient": () => ({ body: patients }) });
    renderApp("/patients");
    const table = await screen.findByRole("table", { name: "Pasienter" });
    expect(within(table).getAllByRole("row")).toHaveLength(3);
    expect(screen.getByText("Viser 1–2 av 2 pasienter")).toBeInTheDocument();
  });

  it("filters the list from the header search", async () => {
    stubApi({ "GET /api/patient": () => ({ body: patients }) });
    renderApp("/patients");
    await screen.findByRole("table", { name: "Pasienter" });
    await userEvent.type(screen.getByRole("searchbox"), "nord");
    const table = screen.getByRole("table", { name: "Pasienter" });
    expect(within(table).getAllByRole("row")).toHaveLength(2);
    expect(screen.getByText("Filtrert på «nord»")).toBeInTheDocument();
  });

  it("opens Pasienter filtered when Enter is pressed in the header search from Start", async () => {
    stubApi({ "GET /api/patient": () => ({ body: patients }) });
    const { router } = renderApp("/");
    const search = await screen.findByRole("searchbox");
    await userEvent.type(search, "ola{Enter}");
    expect(router.state.location.pathname).toBe("/patients");
    expect(await screen.findByText("Filtrert på «ola»")).toBeInTheDocument();
  });

  it("creates a patient locally and refreshes the list", async () => {
    let listed = patients.slice(0, 1);
    const calls = stubApi({
      "GET /api/patient": () => ({ body: listed }),
      "POST /api/patient": () => {
        listed = patients;
        return { body: patients[1] };
      },
    });
    renderApp("/patients");
    await screen.findByRole("table", { name: "Pasienter" });
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Ny pasient" }));
    await user.type(screen.getByLabelText("Fornavn"), "Ola");
    await user.type(screen.getByLabelText("Etternavn"), "Nordmann");
    await user.type(screen.getByLabelText("Fødselsnummer"), "02029054321");
    await user.click(screen.getByRole("button", { name: "Lagre pasient" }));
    expect(await screen.findByText("Pasient lagret")).toBeInTheDocument();
    expect(await screen.findByText("Viser 1–2 av 2 pasienter")).toBeInTheDocument();
    expect(calls.some((c) => c.key === "POST /api/patient")).toBe(true);
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
