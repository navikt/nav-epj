import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CurrentUserContext } from "./currentUser";
import { PatientContext } from "./PatientContext";
import { ShellContext } from "./shellContext";
import { expectNoSeriousViolations } from "./axeHelper";
import type { Pasient } from "../utils/mapping/epj";

const pasient: Pasient = {
  id: "p1",
  fornavn: "Matematisk",
  etternavn: "Ape",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: "1990-01-01",
  gender: "MALE",
};

const user = {
  navn: "Lege Legesen",
  hpr: "9144889",
  autorisasjon: "Lege",
  legekontor: "Testlegekontoret",
  orgnummer: "999888777",
};

function setup(props: Partial<Parameters<typeof PatientContext>[0]> = {}) {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 8, 30));
  const announce = vi.fn();
  const view = render(
    <ShellContext.Provider
      value={{
        narrow: false,
        drawerOpen: false,
        setDrawerOpen: () => {},
        announce,
        rootElement: null,
      }}
    >
      <CurrentUserContext.Provider value={user}>
        <PatientContext pasient={pasient} {...props} />
      </CurrentUserContext.Provider>
    </ShellContext.Provider>,
  );
  return { ...view, announce };
}

describe("PatientContext", () => {
  afterEach(() => vi.useRealTimers());

  it("is a labelled region with the patient name as h1", () => {
    setup();
    const region = screen.getByRole("region", { name: "Pasientkontekst" });
    expect(
      within(region).getByRole("heading", { level: 1, name: "Matematisk Ape" }),
    ).toBeInTheDocument();
  });

  it("shows masked fødselsnummer, born, age, gender, office and org number", () => {
    setup();
    expect(screen.getByText("******12345")).toBeInTheDocument();
    expect(screen.getByText("01.01.1990")).toBeInTheDocument();
    expect(screen.getByText("36 år")).toBeInTheDocument();
    expect(screen.getByText("Mann")).toBeInTheDocument();
    expect(screen.getByText("Testlegekontoret")).toBeInTheDocument();
    expect(screen.getByText("999888777")).toBeInTheDocument();
  });

  it("reveals and hides the fødselsnummer with aria-pressed and announces it", async () => {
    const { announce } = setup();
    const toggle = screen.getByRole("button", { name: "Vis" });
    expect(toggle).toHaveAttribute("aria-pressed", "false");
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(toggle);
    expect(screen.getByText("01019012345")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Skjul" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(announce).toHaveBeenLastCalledWith("Fødselsnummer vises.");
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(
      screen.getByRole("button", { name: "Skjul" }),
    );
    expect(screen.getByText("******12345")).toBeInTheDocument();
    expect(announce).toHaveBeenLastCalledWith("Fødselsnummer skjult.");
  });

  it("renders compact without heading or office and flags stale", () => {
    setup({ compact: true, stale: true });
    expect(screen.queryByRole("heading")).toBeNull();
    expect(screen.getByText("Matematisk Ape")).toHaveClass("name");
    expect(screen.queryByText("Legekontor")).toBeNull();
    expect(screen.getByText("⚠ Utdatert")).toBeInTheDocument();
  });

  it("falls back to values derived from the fødselsnummer", () => {
    setup({ pasient: { ...pasient, birthDate: null, gender: null } });
    expect(screen.getByText("01.01.1990")).toBeInTheDocument();
    expect(screen.getByText("Mann")).toBeInTheDocument();
  });

  it("passes axe", async () => {
    const { container } = setup();
    await expectNoSeriousViolations(container);
  });
});
