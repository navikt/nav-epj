import { act, cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderApp, setupBrowserStubs, stubApi } from "./testApp";
import { expectNoSeriousViolations } from "./axeHelper";
import { useJournalStore } from "./journalStore";

const ape = {
  id: "p1",
  fornavn: "Matematisk",
  etternavn: "Ape",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: "1990-01-01",
  gender: "MALE",
};

const ola = {
  id: "p2",
  fornavn: "Ola",
  etternavn: "Nordmann",
  personident: "02029054321",
  personidentType: "FNR",
  birthDate: "1990-02-02",
  gender: "MALE",
};

function kons(overrides: Record<string, unknown> = {}) {
  return {
    id: "k1",
    pasientId: "p1",
    hpr: ["9144889"],
    journalnotat: [],
    diagnoser: [],
    startetTidspunkt: "2026-09-30T09:00:00",
    avsluttetTidspunkt: null,
    status: "PÅGÅENDE",
    problemstilling: null,
    ...overrides,
  };
}

function api(extra: Parameters<typeof stubApi>[0] = {}) {
  return stubApi({
    "GET /api/patient": () => ({ body: [ape, ola] }),
    "GET /api/patient/p1": () => ({ body: ape }),
    "GET /api/patient/p2": () => ({ body: ola }),
    "GET /api/patients/p1/konsultasjoner": () => ({ body: [kons()] }),
    "GET /api/patients/p2/konsultasjoner": () => ({ body: [] }),
    ...extra,
  });
}

beforeEach(setupBrowserStubs);
afterEach(() => {
  cleanup();
  useJournalStore.getState().clear();
  vi.unstubAllGlobals();
});

const docTab = (name: string | RegExp) =>
  screen.getByRole("tab", { name });

describe("journal deep links", () => {
  it("opens the Journal tab for /patients/$patientId", async () => {
    api();
    renderApp("/patients/p1");
    expect(
      await screen.findByRole("heading", { level: 1, name: "Matematisk Ape" }),
    ).toBeInTheDocument();
    expect(docTab("Journal · Matematisk Ape")).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tabpanel", { name: "Journal · Matematisk Ape" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Pågående konsultasjon" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Pasienter" })).not.toBeInTheDocument();
  });

  it("shows the read-only konsultasjon for /patients/$patientId/konsultasjon/$konsultasjonId", async () => {
    api({
      "GET /api/patients/p1/konsultasjoner": () => ({
        body: [
          kons({ id: "k9", status: "FULLFØRT", avsluttetTidspunkt: "2026-09-01T10:00:00", startetTidspunkt: "2026-09-01T09:00:00" }),
        ],
      }),
    });
    renderApp("/patients/p1/konsultasjon/k9");
    expect(
      await screen.findByRole("heading", { name: "Fullført konsultasjon" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Konsultasjon" })).toHaveAttribute("aria-selected", "true");
  });

  it("shows the task pane and status bar for the active patient", async () => {
    api();
    renderApp("/patients/p1");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    const nav = screen.getByRole("navigation", { name: "Oppgaver" });
    expect(within(nav).getByText("Matematisk Ape")).toBeInTheDocument();
    expect(within(nav).getByRole("button", { name: "Aktiv journal" })).toHaveAttribute("aria-current", "page");
    expect(
      screen.getByRole("button", { name: "Aktiv pasient: Matematisk Ape. Åpne journal." }),
    ).toBeInTheDocument();
    expect(screen.getByText(/^◐ Konsultasjon \d+ min$/)).toBeInTheDocument();
  });

  it("opens a patient's journal from Pasienter and can return to the list tab", async () => {
    api();
    const { router } = renderApp("/patients");
    const table = await screen.findByRole("table", { name: "Pasienter" });
    await userEvent.click(within(table).getAllByRole("button", { name: /Åpne journal/ })[0]);
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    expect(router.state.location.pathname).toBe("/patients/p1");
    expect(docTab("Journal · Matematisk Ape")).toHaveAttribute("aria-selected", "true");
    await userEvent.click(docTab("Pasienter"));
    expect(router.state.location.pathname).toBe("/patients");
    await userEvent.click(docTab("Journal · Matematisk Ape"));
    expect(router.state.location.pathname).toBe("/patients/p1");
    expect(await screen.findByRole("heading", { name: "Matematisk Ape" })).toBeInTheDocument();
  });

  it("passes axe with the journal open", async () => {
    api();
    const { container } = renderApp("/patients/p1");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    await expectNoSeriousViolations(container);
  });
});

describe("journal sub-tab in the URL", () => {
  const done = kons({
    id: "k9",
    status: "FULLFØRT",
    avsluttetTidspunkt: "2026-09-01T10:00:00",
    startetTidspunkt: "2026-09-01T09:00:00",
  });
  const withHistory = () =>
    api({
      "GET /api/patients/p1/konsultasjoner": () => ({ body: [kons(), done] }),
    });
  const selected = (name: string | RegExp) =>
    expect(screen.getByRole("tab", { name })).toHaveAttribute("aria-selected", "true");

  it("opens the sub-tab named by the tab search param", async () => {
    withHistory();
    renderApp("/patients/p1?tab=tidligere");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    selected(/^Tidligere konsultasjoner/);
    expect(screen.getByRole("table")).toBeInTheDocument();
  });

  it("falls back to Konsultasjon for an unknown tab value", async () => {
    withHistory();
    renderApp("/patients/p1?tab=nonsense");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    selected("Konsultasjon");
    expect(screen.getByRole("heading", { name: "Pågående konsultasjon" })).toBeInTheDocument();
  });

  it("writes the chosen sub-tab to the URL by replacing the entry and drops it for Konsultasjon", async () => {
    withHistory();
    const { router } = renderApp("/patients/p1");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    const length = router.history.length;
    await userEvent.click(screen.getByRole("tab", { name: /^Apper/ }));
    await waitFor(() => expect(router.state.location.search).toEqual({ tab: "apper" }));
    selected(/^Apper/);
    await userEvent.click(screen.getByRole("tab", { name: "Konsultasjon" }));
    await waitFor(() => expect(router.state.location.search).toEqual({}));
    selected("Konsultasjon");
    expect(router.history.length).toBe(length);
  });

  it("follows the URL when it changes", async () => {
    withHistory();
    const { router } = renderApp("/patients/p1");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    act(() => {
      void router.navigate({
        to: "/patients/$patientId",
        params: { patientId: "p1" },
        search: { tab: "tidligere" },
      });
    });
    await waitFor(() => selected(/^Tidligere konsultasjoner/));
  });

  it("keeps the chosen sub-tab when the Journal tab is activated again", async () => {
    withHistory();
    const { router } = renderApp("/patients/p1?tab=tidligere");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    act(() => {
      void router.navigate({ to: "/patients" });
    });
    await screen.findByRole("heading", { name: "Pasienter" });
    await userEvent.click(docTab("Journal · Matematisk Ape"));
    await waitFor(() => expect(router.state.location.pathname).toBe("/patients/p1"));
    expect(router.state.location.search).toEqual({ tab: "tidligere" });
    selected(/^Tidligere konsultasjoner/);
  });

  it("leaves the konsultasjon route when another sub-tab is chosen", async () => {
    withHistory();
    const { router } = renderApp("/patients/p1/konsultasjon/k9");
    await screen.findByRole("heading", { name: "Fullført konsultasjon" });
    await userEvent.click(screen.getByRole("tab", { name: /^Tidligere konsultasjoner/ }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/patients/p1"));
    expect(router.state.location.search).toEqual({ tab: "tidligere" });
    expect(screen.getByRole("table")).toBeInTheDocument();
  });

  it("ignores the tab param on a konsultasjon route", async () => {
    withHistory();
    renderApp("/patients/p1/konsultasjon/k9?tab=apper");
    expect(await screen.findByRole("heading", { name: "Fullført konsultasjon" })).toBeInTheDocument();
    selected("Konsultasjon");
  });
});

describe("journal guards", () => {
  it("guards closing the tab with unsaved changes", async () => {
    const calls = api({
      "PATCH /api/patients/p1/konsultasjoner": () => ({ body: {} }),
    });
    const { router } = renderApp("/patients/p1");
    await userEvent.type(await screen.findByLabelText("Journalnotat"), "Hei");
    expect(docTab("Journal · Matematisk Ape •")).toBeInTheDocument();

    await userEvent.keyboard("{Control>}w{/Control}");
    const dialog = await screen.findByRole("alertdialog", { name: "Lukke journalen uten å lagre?" });
    await expectNoSeriousViolations(document.body);
    await userEvent.click(within(dialog).getByRole("button", { name: "Avbryt" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(useJournalStore.getState().draft.notat).toBe("Hei");

    await userEvent.keyboard("{Control>}w{/Control}");
    await userEvent.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Lagre og lukk" }),
    );
    await waitFor(() => expect(router.state.location.pathname).toBe("/"));
    expect(calls.find((c) => c.key.startsWith("PATCH"))).toBeDefined();
    expect(screen.queryByRole("tab", { name: /Journal ·/ })).not.toBeInTheDocument();
    expect(useJournalStore.getState().patientId).toBeNull();
  });

  it("closes without saving on request", async () => {
    const calls = api();
    renderApp("/patients/p1");
    await userEvent.type(await screen.findByLabelText("Journalnotat"), "Hei");
    await userEvent.keyboard("{Control>}w{/Control}");
    await userEvent.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Lukk uten å lagre" }),
    );
    await waitFor(() =>
      expect(screen.queryByRole("tab", { name: /Journal ·/ })).not.toBeInTheDocument(),
    );
    expect(calls.some((c) => c.key.startsWith("PATCH"))).toBe(false);
  });

  it("closes a clean journal tab without asking", async () => {
    api();
    renderApp("/patients/p1");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    await userEvent.keyboard("{Control>}w{/Control}");
    await waitFor(() =>
      expect(screen.queryByRole("tab", { name: /Journal ·/ })).not.toBeInTheDocument(),
    );
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("asks before switching patient and warns about unsaved changes", async () => {
    api();
    const { router } = renderApp("/patients/p1");
    await userEvent.type(await screen.findByLabelText("Journalnotat"), "Hei");
    await userEvent.click(screen.getByRole("button", { name: /Finn en annen pasient/ }));
    const table = await screen.findByRole("table", { name: "Pasienter" });
    const rows = within(table).getAllByRole("row");
    await userEvent.click(within(rows[2]).getByRole("button", { name: /Åpne journal/ }));
    const dialog = await screen.findByRole("alertdialog", {
      name: "Bytte fra Matematisk Ape til Ola Nordmann?",
    });
    expect(within(dialog).getByText(/ulagrede endringer/)).toBeInTheDocument();
    await expectNoSeriousViolations(document.body);
    await userEvent.click(within(dialog).getByRole("button", { name: "Avbryt" }));
    expect(router.state.location.pathname).toBe("/patients");
    expect(useJournalStore.getState().patientId).toBe("p1");

    await userEvent.click(within(rows[2]).getByRole("button", { name: /Åpne journal/ }));
    await userEvent.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Lukk og bytt pasient" }),
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Ola Nordmann" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/patients/p2");
    expect(screen.getByText("Pasient byttet til Ola Nordmann")).toBeInTheDocument();
    expect(screen.getAllByRole("tab").filter((t) => /Journal ·/.test(t.textContent ?? ""))).toHaveLength(1);
  });

  it("opens the same patient's journal again without a dialog", async () => {
    api();
    const { router } = renderApp("/patients/p1");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    await userEvent.click(screen.getByRole("button", { name: /Finn en annen pasient/ }));
    const table = await screen.findByRole("table", { name: "Pasienter" });
    await userEvent.click(within(within(table).getAllByRole("row")[1]).getByRole("button", { name: /Åpne journal/ }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    await waitFor(() => expect(router.state.location.pathname).toBe("/patients/p1"));
  });

  it("asks before a route change switches the open journal and leaves everything untouched on cancel", async () => {
    api();
    const { router } = renderApp("/patients/p1");
    await userEvent.type(await screen.findByLabelText("Journalnotat"), "Hei");
    const href = router.state.location.href;
    const length = router.history.length;
    act(() => {
      void router.navigate({ to: "/patients/$patientId", params: { patientId: "p2" } });
    });
    const dialog = await screen.findByRole("alertdialog", {
      name: "Bytte fra Matematisk Ape til Ola Nordmann?",
    });
    expect(within(dialog).getByText("Du åpnet en lenke til Ola Nordmann.")).toBeInTheDocument();
    expect(within(dialog).getByText(/ulagrede endringer/)).toBeInTheDocument();
    expect(useJournalStore.getState().patientId).toBe("p1");
    await userEvent.click(within(dialog).getByRole("button", { name: "Avbryt" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    expect(router.state.location.href).toBe(href);
    expect(router.history.length).toBe(length);
    expect(useJournalStore.getState().patientId).toBe("p1");
    expect(screen.getByLabelText("Journalnotat")).toHaveValue("Hei");
  });

  it("switches for a route change only after confirmation, also when nothing is unsaved", async () => {
    api();
    const { router } = renderApp("/patients/p1");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    act(() => {
      void router.navigate({ to: "/patients/$patientId", params: { patientId: "p2" } });
    });
    const dialog = await screen.findByRole("alertdialog");
    expect(within(dialog).queryByText(/ulagrede endringer/)).not.toBeInTheDocument();
    expect(useJournalStore.getState().patientId).toBe("p1");
    await userEvent.click(within(dialog).getByRole("button", { name: "Lukk og bytt pasient" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Ola Nordmann" })).toBeInTheDocument();
    expect(useJournalStore.getState().patientId).toBe("p2");
    expect(router.state.location.pathname).toBe("/patients/p2");
  });

  it("keeps the konsultasjon of a deep link when the switch is confirmed and adds one history entry", async () => {
    api({
      "GET /api/patients/p2/konsultasjoner": () => ({
        body: [
          kons({
            id: "k9",
            pasientId: "p2",
            status: "FULLFØRT",
            avsluttetTidspunkt: "2026-09-01T10:00:00",
            startetTidspunkt: "2026-09-01T09:00:00",
          }),
        ],
      }),
    });
    const { router } = renderApp("/patients/p1");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    const length = router.history.length;
    act(() => {
      void router.navigate({
        to: "/patients/$patientId/konsultasjon/$konsultasjonId",
        params: { patientId: "p2", konsultasjonId: "k9" },
      });
    });
    await userEvent.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Lukk og bytt pasient" }),
    );
    expect(await screen.findByRole("heading", { name: "Fullført konsultasjon" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/patients/p2/konsultasjon/k9");
    expect(useJournalStore.getState().selectedKonsultasjonId).toBe("k9");
    expect(router.history.length).toBe(length + 1);
  });

  it("restores the exact previous location when back navigation is cancelled", async () => {
    api();
    const { router } = renderApp("/patients/p1", { browserHistory: true });
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    await userEvent.click(screen.getByRole("button", { name: /Finn en annen pasient/ }));
    await screen.findByRole("table", { name: "Pasienter" });
    await userEvent.click(screen.getByRole("tab", { name: /Journal ·/ }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/patients/p1"));
    act(() => {
      void router.navigate({ to: "/patients/$patientId", params: { patientId: "p2" } });
    });
    await userEvent.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Lukk og bytt pasient" }),
    );
    await screen.findByRole("heading", { level: 1, name: "Ola Nordmann" });
    const href = router.state.location.href;
    const length = router.history.length;
    act(() => window.history.back());
    const dialog = await screen.findByRole("alertdialog", {
      name: "Bytte fra Ola Nordmann til Matematisk Ape?",
    });
    await userEvent.click(within(dialog).getByRole("button", { name: "Avbryt" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    expect(router.state.location.href).toBe(href);
    expect(router.history.length).toBe(length);
    expect(useJournalStore.getState().patientId).toBe("p2");
  });

  it("does not claim a link was opened when the switch started in the app", async () => {
    api();
    renderApp("/patients/p1");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    await userEvent.type(screen.getByRole("searchbox"), "ola{Enter}");
    const dialog = await screen.findByRole("alertdialog");
    expect(within(dialog).queryByText(/Du åpnet en lenke/)).not.toBeInTheDocument();
  });

  it("moves focus to the journal tab after a confirmed switch", async () => {
    api();
    const { router } = renderApp("/patients/p1");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    act(() => {
      void router.navigate({ to: "/patients/$patientId", params: { patientId: "p2" } });
    });
    await userEvent.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Lukk og bytt pasient" }),
    );
    await waitFor(() => expect(screen.getByRole("tab", { name: /Journal ·/ })).toHaveFocus());
  });

  it("moves focus to the remaining tab after closing without saving", async () => {
    api();
    renderApp("/patients/p1");
    await userEvent.type(await screen.findByLabelText("Journalnotat"), "Hei");
    await userEvent.keyboard("{Control>}w{/Control}");
    await userEvent.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Lukk uten å lagre" }),
    );
    await waitFor(() => expect(screen.getByRole("tab", { name: "Start" })).toHaveFocus());
  });

  it("warns before the page unloads only while the draft is dirty", async () => {
    api();
    renderApp("/patients/p1", { browserHistory: true });
    const notat = await screen.findByLabelText("Journalnotat");
    const unload = () => {
      const event = new Event("beforeunload", { cancelable: true });
      window.dispatchEvent(event);
      return event.defaultPrevented;
    };
    expect(unload()).toBe(false);
    await userEvent.type(notat, "Hei");
    expect(unload()).toBe(true);
  });
});

describe("header search Enter", () => {
  it("opens the journal when exactly one patient matches", async () => {
    api();
    const { router } = renderApp("/");
    await userEvent.type(await screen.findByRole("searchbox"), "ola{Enter}");
    expect(await screen.findByRole("heading", { level: 1, name: "Ola Nordmann" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/patients/p2");
  });

  it("opens the filtered list when several patients match", async () => {
    api();
    const { router } = renderApp("/");
    await userEvent.type(await screen.findByRole("searchbox"), "a{Enter}");
    expect(await screen.findByRole("table", { name: "Pasienter" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/patients");
  });

  it("goes through the S7 dialog when another journal is open", async () => {
    api();
    renderApp("/patients/p1");
    await screen.findByRole("heading", { name: "Matematisk Ape" });
    await userEvent.type(screen.getByRole("searchbox"), "ola{Enter}");
    expect(
      await screen.findByRole("alertdialog", { name: "Bytte fra Matematisk Ape til Ola Nordmann?" }),
    ).toBeInTheDocument();
    expect(useJournalStore.getState().patientId).toBe("p1");
  });
});
