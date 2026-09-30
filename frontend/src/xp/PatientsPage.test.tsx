import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { PatientsPage } from "./PatientsPage";
import { usePatientsStore } from "./patientsStore";
import { expectNoSeriousViolations } from "./axeHelper";
import type { Pasient } from "../utils/mapping/epj";

const make = (i: number): Pasient => ({
  id: String(i),
  fornavn: `Pasient${String(i).padStart(2, "0")}`,
  etternavn: "Ape",
  personident: `0101901${String(i).padStart(4, "0")}`,
  personidentType: "FNR",
  birthDate: "1990-01-01",
  gender: "MALE",
});

function seed(count: number) {
  act(() =>
    usePatientsStore.setState({
      status: "ready",
      patients: Array.from({ length: count }, (_, i) => make(i + 1)),
    }),
  );
}

function setup(canCreate = true) {
  const onNewPatient = vi.fn();
  const onOpenJournal = vi.fn();
  const view = render(
    <PatientsPage
      canCreate={canCreate}
      onNewPatient={onNewPatient}
      onOpenJournal={onOpenJournal}
    />,
  );
  return { ...view, onNewPatient, onOpenJournal };
}

describe("PatientsPage", () => {
  it("loads patients on mount when idle", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: true, status: 200, json: async () => [make(1)] })),
    );
    setup();
    expect(screen.getByRole("status")).toHaveTextContent("Laster …");
    expect(await screen.findByText("Pasient01 Ape")).toBeInTheDocument();
    vi.unstubAllGlobals();
  });

  it("shows an error with retry when loading fails", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: false, status: 500, json: async () => ({}) })));
    setup();
    expect(await screen.findByRole("alert")).toHaveTextContent("Feil ved lasting av pasienter");
    expect(screen.getByRole("button", { name: "Prøv igjen" })).toBeInTheDocument();
    vi.unstubAllGlobals();
  });

  it("lists patients with masked fødselsnummer, born value and the open button", async () => {
    seed(3);
    const { container } = setup();
    expect(screen.getByRole("heading", { level: 1, name: "Pasienter" })).toBeInTheDocument();
    const table = screen.getByRole("table", { name: "Pasienter" });
    const rows = within(table).getAllByRole("row");
    expect(rows).toHaveLength(4);
    expect(within(rows[1]).getByText("******10001")).toBeInTheDocument();
    expect(within(rows[1]).getByText(/^\d\d\.\d\d\.1990 \(\d+ år\)$/)).toBeInTheDocument();
    expect(within(rows[1]).getByText("Fødselsnummer skjult, slutter på 10001")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Åpne journal for Pasient01 Ape" })).toBeInTheDocument();
    await expectNoSeriousViolations(container);
  });

  it("opens the journal for the clicked row", async () => {
    seed(2);
    const { onOpenJournal } = setup();
    await userEvent.click(screen.getByRole("button", { name: "Åpne journal for Pasient02 Ape" }));
    expect(onOpenJournal).toHaveBeenCalledWith(expect.objectContaining({ id: "2" }));
  });

  it("shows the pager strings and pages through the list", async () => {
    seed(30);
    setup();
    const user = userEvent.setup();
    expect(screen.getByText("Viser 1–25 av 30 pasienter")).toBeInTheDocument();
    expect(screen.getByText("Side 1 av 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Forrige side" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Neste side" }));
    expect(screen.getByText("Viser 26–30 av 30 pasienter")).toBeInTheDocument();
    expect(screen.getByText("Side 2 av 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Neste side" })).toBeDisabled();
  });

  it("filters by the search query and offers to clear it", async () => {
    seed(30);
    setup();
    act(() => usePatientsStore.getState().setQuery("Pasient07"));
    expect(screen.getByText("Filtrert på «Pasient07»")).toBeInTheDocument();
    expect(screen.getAllByRole("row")).toHaveLength(2);
    expect(screen.getByText(/Viser 1–1 av 1 pasienter/)).toHaveTextContent("(filtrert fra 30)");
    await userEvent.click(screen.getByRole("button", { name: "Tøm søk" }));
    expect(usePatientsStore.getState().query).toBe("");
    expect(screen.getByText("Viser 1–25 av 30 pasienter")).toBeInTheDocument();
  });

  it("shows the empty search state", async () => {
    seed(3);
    setup();
    act(() => usePatientsStore.getState().setQuery("zzz"));
    expect(screen.getByText("Ingen pasienter passer til «zzz».")).toBeInTheDocument();
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("shows the no-patients state with a create button only when allowed", () => {
    seed(0);
    const { unmount } = setup(true);
    expect(screen.getByText("Du har ingen pasienter ennå.")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Ny pasient" })).toHaveLength(2);
    unmount();
    setup(false);
    expect(screen.queryByRole("button", { name: "Ny pasient" })).toBeNull();
    expect(screen.queryByText("⚙ Kun lokalt")).toBeNull();
  });

  it("shows the local-only badge with the create button", async () => {
    seed(1);
    const { onNewPatient } = setup(true);
    expect(screen.getByText("⚙ Kun lokalt")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Ny pasient" }));
    expect(onNewPatient).toHaveBeenCalled();
  });

  it("switches to recently opened patients", async () => {
    seed(3);
    act(() => usePatientsStore.getState().markOpened("2"));
    setup();
    expect(screen.getByRole("button", { name: "Mine pasienter (3)" })).toHaveAttribute("aria-pressed", "true");
    await userEvent.click(screen.getByRole("button", { name: "Nylig åpnet (1)" }));
    expect(screen.getAllByRole("row")).toHaveLength(2);
    expect(screen.getByText("Pasient02 Ape")).toBeInTheDocument();
  });

  it("shows an empty state for recently opened when none were opened", async () => {
    seed(3);
    setup();
    await userEvent.click(screen.getByRole("button", { name: "Nylig åpnet (0)" }));
    expect(screen.getByText("Du har ikke åpnet noen journaler ennå.")).toBeInTheDocument();
  });

  it("shows the last konsultasjon when known", () => {
    seed(2);
    act(() => {
      usePatientsStore.getState().setLastKonsultasjon("1", { status: "PÅGÅENDE", tidspunkt: "2026-01-02T10:00:00" });
      usePatientsStore.getState().setLastKonsultasjon("2", { status: "FULLFØRT", tidspunkt: "2026-01-03T10:00:00" });
    });
    setup();
    expect(screen.getByText("Pågår nå")).toBeInTheDocument();
    expect(screen.getByText("03.01.2026")).toBeInTheDocument();
  });
});
