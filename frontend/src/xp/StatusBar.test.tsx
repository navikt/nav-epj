import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { StatusBar } from "./StatusBar";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import { kari, ola, seedJournal } from "./appFixtures";
import { useAppDialogStore } from "./appDialogStore";
import { useAppRunStore } from "./appRunStore";

describe("StatusBar", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["setInterval", "clearInterval", "Date"] });
    vi.setSystemTime(new Date(2026, 8, 30, 9, 14, 58));
  });

  afterEach(() => vi.useRealTimers());

  it("is a labelled contentinfo landmark", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    expect(
      screen.getByRole("contentinfo", { name: copy["status.label"] }),
    ).toBeInTheDocument();
  });

  it("shows the ready status in a status region", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    expect(screen.getByRole("status")).toHaveTextContent(copy["status.ready"]);
  });

  it("shows no active patient and opens the patient list", async () => {
    vi.useRealTimers();
    const onOpenPatients = vi.fn();
    render(<StatusBar onOpenPatients={onOpenPatients} />);
    const button = screen.getByRole("button", {
      name: copy["status.noPatient.aria"],
    });
    expect(button).toHaveTextContent(copy["status.noPatient"]);
    await userEvent.click(button);
    expect(onOpenPatients).toHaveBeenCalledOnce();
  });

  it("shows the TEST marker", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    expect(
      within(screen.getByRole("contentinfo")).getByTitle(copy["app.testTooltip"]),
    ).toHaveTextContent(copy["app.test"]);
  });

  it("shows the clock and gives screen readers the full sentence", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    expect(screen.getByText("09:14")).toHaveAttribute("aria-hidden", "true");
    expect(screen.getByText(copy["status.clock.sr"]("09:14"))).toBeInTheDocument();
  });

  it("updates the clock every second", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(screen.getByText("09:15")).toBeInTheDocument();
    expect(screen.getByText(copy["status.clock.sr"]("09:15"))).toBeInTheDocument();
  });

  it("does not render consultation or tab app segments without state", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    expect(screen.queryByText(/Konsultasjon/)).not.toBeInTheDocument();
    expect(screen.queryByText(/egen fane/)).not.toBeInTheDocument();
  });

  it("has no serious accessibility violations", async () => {
    vi.useRealTimers();
    const { container } = render(<StatusBar onOpenPatients={vi.fn()} />);
    await expectNoSeriousViolations(container);
  });

  it("shows the active patient and the ongoing konsultasjon time, and opens the journal", async () => {
    vi.useRealTimers();
    useJournalStore.setState({
      patientId: "p1",
      patient: {
        id: "p1",
        fornavn: "Matematisk",
        etternavn: "Ape",
        personident: "01019012345",
        personidentType: "FNR" as const,
        birthDate: "1990-01-01",
        gender: "MALE" as const,
      },
      konsultasjoner: [
        {
          id: "k1",
          pasientId: "p1",
          hpr: [],
          journalnotat: [],
          diagnoser: [],
          startetTidspunkt: new Date(Date.now() - 12 * 60_000).toISOString(),
          avsluttetTidspunkt: null,
          status: "PÅGÅENDE",
          problemstilling: null,
        },
      ],
    });
    const onOpenJournal = vi.fn();
    render(<StatusBar onOpenPatients={vi.fn()} onOpenJournal={onOpenJournal} />);
    expect(screen.getByText("◐ Konsultasjon 12 min")).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: "Aktiv pasient: Matematisk Ape. Åpne journal." }),
    );
    expect(onOpenJournal).toHaveBeenCalledOnce();
    expect(screen.queryByText(copy["status.noPatient"])).not.toBeInTheDocument();
    useJournalStore.getState().clear();
  });

  describe("tab apps", () => {
    beforeEach(() => {
      vi.useRealTimers();
      seedJournal();
      useAppRunStore.getState().addTabApp({
        id: "smart-ny-fane-1",
        clientId: "ny-fane",
        navn: "Fanen",
        patient: ola,
        startedAt: new Date(2026, 8, 30, 9, 14),
      });
    });

    it("shows how many apps run in their own tab and opens their dialog", async () => {
      render(<StatusBar onOpenPatients={vi.fn()} />);
      const segment = screen.getByRole("button", { name: copy["status.tabApps"](1) });
      await userEvent.click(segment);
      expect(useAppDialogStore.getState().dialog).toEqual({
        kind: "tabApp",
        tabId: "smart-ny-fane-1",
      });
    });

    it("flags apps that belong to the previous patient instead", async () => {
      seedJournal(kari);
      render(<StatusBar onOpenPatients={vi.fn()} />);
      expect(screen.queryByRole("button", { name: copy["status.tabApps"](1) })).toBeNull();
      await userEvent.click(
        screen.getByRole("button", { name: copy["status.staleApps"](1) }),
      );
      expect(useAppDialogStore.getState().dialog).toEqual({
        kind: "tabApp",
        tabId: "smart-ny-fane-1",
      });
    });

    it("has no serious accessibility violations", async () => {
      const { container } = render(<StatusBar onOpenPatients={vi.fn()} />);
      await expectNoSeriousViolations(container);
    });
  });
});
