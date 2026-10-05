import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { copy } from "./copy";
import { useJournalStore, type JournalSubTab } from "./journalStore";
import type { CurrentRoute } from "./useCurrentRoute";
import { useRouteTabSync } from "./useRouteTabSync";
import { useWorkspaceStore } from "./workspaceStore";

const store = () => useWorkspaceStore.getState();

const start: CurrentRoute = { kind: "start" };
const patients: CurrentRoute = { kind: "patients" };
const journal = (
  patientId: string,
  konsultasjonId?: string,
  tab?: JournalSubTab,
): CurrentRoute => ({
  kind: "journal",
  patientId,
  konsultasjonId,
  tab,
});

beforeEach(() => store().reset());

describe("useRouteTabSync", () => {
  it("keeps Start selected on the front page", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync(start, navigate));
    expect(store().current).toBe("start");
    expect(store().tabs.map((t) => t.id)).toEqual(["start"]);
    expect(navigate).not.toHaveBeenCalled();
  });

  it("opens and selects the Pasienter tab on a deep link without redirecting", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync(patients, navigate));
    expect(store().current).toBe("patients");
    expect(store().tabs.map((t) => t.label)).toEqual([
      copy["tabs.start"],
      copy["pane.system.patients"],
    ]);
    expect(store().tabs[1].closable).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it("maps unknown routes to no tab", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync({ kind: "other" }, navigate));
    expect(store().tabs.map((t) => t.id)).toEqual(["start"]);
    expect(navigate).not.toHaveBeenCalled();
  });

  it("goes to Pasienter from a journal route when its tab is activated", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync(journal("abc"), navigate));
    act(() => {
      store().openTab({ kind: "patients", label: copy["pane.system.patients"] });
    });
    expect(navigate).toHaveBeenCalledExactlyOnceWith({ to: "/patients" });
  });

  it("follows route changes without duplicating the tab", () => {
    const navigate = vi.fn();
    const { rerender } = renderHook(
      ({ route }) => useRouteTabSync(route, navigate),
      { initialProps: { route: start as CurrentRoute } },
    );
    rerender({ route: patients });
    expect(store().current).toBe("patients");
    rerender({ route: start });
    expect(store().current).toBe("start");
    rerender({ route: patients });
    expect(store().tabs.filter((t) => t.kind === "patients")).toHaveLength(1);
    expect(navigate).not.toHaveBeenCalled();
  });

  it("navigates to the route of a tab activated from the tab strip", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync(patients, navigate));
    act(() => store().setCurrent("start"));
    expect(navigate).toHaveBeenCalledExactlyOnceWith({ to: "/" });
  });

  it("navigates to Start when the Pasienter tab is closed", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync(patients, navigate));
    act(() => store().closeTab("patients"));
    expect(store().current).toBe("start");
    expect(navigate).toHaveBeenCalledExactlyOnceWith({ to: "/" });
  });

  it("navigates to Pasienter when its tab is reactivated", () => {
    const navigate = vi.fn();
    const { rerender } = renderHook(
      ({ route }) => useRouteTabSync(route, navigate),
      { initialProps: { route: patients as CurrentRoute } },
    );
    rerender({ route: start });
    act(() => store().setCurrent("patients"));
    expect(navigate).toHaveBeenCalledExactlyOnceWith({ to: "/patients" });
  });

  it("does not navigate when a non-routed tab is selected", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync(patients, navigate));
    act(() => {
      store().openTab({ kind: "kontrollpanel", label: "Kontrollpanel" });
    });
    expect(navigate).not.toHaveBeenCalled();
  });
});

const pasient = {
  id: "p1",
  fornavn: "Matematisk",
  etternavn: "Ape",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: "1990-01-01",
  gender: "MALE",
};

function stubJournalApi() {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: true,
      status: 200,
      json: async () =>
        url.endsWith("/konsultasjoner")
          ? []
          : url === "/api/active-patient"
            ? { patientId: "p1", expiresAt: "2026-09-30T17:14:00Z" }
            : { ...pasient, id: url.split("/").at(-1), fornavn: `Fornavn ${url.split("/").at(-1)}` },
    })),
  );
}

describe("useRouteTabSync journal routes", () => {
  beforeEach(stubJournalApi);
  afterEach(() => {
    useJournalStore.getState().clear();
    vi.unstubAllGlobals();
  });

  it("refreshes consultation data when returning from an embedded app tab", async () => {
    renderHook(() => useRouteTabSync(journal("p1"), vi.fn()));
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    act(() => store().openTab({ kind: "app", clientId: "test-app", label: "SMART" }));
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify([{
      id: "k1",
      pasientId: "p1",
      status: "PÅGÅENDE",
      startetTidspunkt: "2026-09-30T09:00:00",
      avsluttetTidspunkt: null,
      problemstilling: null,
      hpr: ["9144889"],
      diagnoser: [],
      journalnotat: [{
        id: "n1", konsultasjonId: "k1", pasientId: "p1", journalnotat: "Fra SMART",
      }],
    }])));
    act(() => store().setCurrent("journal"));
    await waitFor(() => expect(useJournalStore.getState().draft.notat).toBe("Fra SMART"));
  });

  it("opens and selects the single Journal tab for a patient route and loads that patient", async () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync(journal("p1"), navigate));
    expect(store().current).toBe("journal");
    expect(store().tabs.map((t) => t.id)).toEqual(["start", "journal"]);
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    expect(useJournalStore.getState().patientId).toBe("p1");
    expect(store().tabs[1].label).toBe("Journal · Fornavn p1 Ape");
    expect(navigate).not.toHaveBeenCalled();
  });

  it("selects the konsultasjon from the route", async () => {
    renderHook(() => useRouteTabSync(journal("p1", "k7"), vi.fn()));
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    expect(useJournalStore.getState().selectedKonsultasjonId).toBe("k7");
  });

  it("keeps a single Journal tab across journal routes and leaves the list route to Pasienter", async () => {
    const { rerender } = renderHook(
      ({ route }) => useRouteTabSync(route, vi.fn()),
      { initialProps: { route: journal("p1") as CurrentRoute } },
    );
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    rerender({ route: patients });
    expect(store().current).toBe("patients");
    rerender({ route: journal("p1", "k2") });
    expect(store().current).toBe("journal");
    expect(store().tabs.filter((t) => t.kind === "journal")).toHaveLength(1);
    expect(useJournalStore.getState().selectedKonsultasjonId).toBe("k2");
  });

  it("navigates to the open journal, including its konsultasjon, when the Journal tab is activated", async () => {
    const navigate = vi.fn();
    const { rerender } = renderHook(
      ({ route }) => useRouteTabSync(route, navigate),
      { initialProps: { route: journal("p1", "k2") as CurrentRoute } },
    );
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    rerender({ route: patients });
    navigate.mockClear();
    act(() => store().setCurrent("journal"));
    expect(navigate).toHaveBeenCalledExactlyOnceWith({
      to: "/patients/$patientId/konsultasjon/$konsultasjonId",
      params: { patientId: "p1", konsultasjonId: "k2" },
    });
  });

  it("navigates to the plain journal route when no konsultasjon is selected", async () => {
    const navigate = vi.fn();
    const { rerender } = renderHook(
      ({ route }) => useRouteTabSync(route, navigate),
      { initialProps: { route: journal("p1") as CurrentRoute } },
    );
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    rerender({ route: patients });
    navigate.mockClear();
    act(() => store().setCurrent("journal"));
    expect(navigate).toHaveBeenCalledExactlyOnceWith({
      to: "/patients/$patientId",
      params: { patientId: "p1" },
    });
  });

  it("does not navigate when the Journal tab is activated while already on a journal route", async () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync(journal("p1", "k1"), navigate));
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    navigate.mockClear();
    act(() => store().setCurrent("journal"));
    expect(navigate).not.toHaveBeenCalled();
  });

  it("shows the sub-tab named by the route", async () => {
    renderHook(() => useRouteTabSync(journal("p1", undefined, "tidligere"), vi.fn()));
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    expect(useJournalStore.getState().subTab).toBe("tidligere");
  });

  it("retries a failed journal load when the URL changes sub-tab", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: false, status: 500 })));
    const { rerender } = renderHook(
      ({ route }) => useRouteTabSync(route, vi.fn()),
      { initialProps: { route: journal("p1") as CurrentRoute } },
    );
    await waitFor(() => expect(useJournalStore.getState().status).toBe("error"));
    stubJournalApi();
    rerender({ route: journal("p1", undefined, "tidligere") });
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    expect(useJournalStore.getState().patient?.id).toBe("p1");
    expect(useJournalStore.getState().subTab).toBe("tidligere");
  });

  it("shows Konsultasjon when the route names no sub-tab or a konsultasjon", async () => {
    const { rerender } = renderHook(
      ({ route }) => useRouteTabSync(route, vi.fn()),
      { initialProps: { route: journal("p1", undefined, "apper") as CurrentRoute } },
    );
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    rerender({ route: journal("p1") });
    expect(useJournalStore.getState().subTab).toBe("konsultasjon");
    rerender({ route: journal("p1", undefined, "apper") });
    expect(useJournalStore.getState().subTab).toBe("apper");
    rerender({ route: journal("p1", "k2", "apper") });
    expect(useJournalStore.getState().subTab).toBe("konsultasjon");
  });

  it("keeps the sub-tab of the route when another patient is opened", async () => {
    const navigate = vi.fn();
    const { rerender } = renderHook(
      ({ route }) => useRouteTabSync(route, navigate),
      { initialProps: { route: journal("p1", undefined, "tidligere") as CurrentRoute } },
    );
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    rerender({ route: journal("p2", undefined, "apper") });
    await waitFor(() => expect(useJournalStore.getState().patientId).toBe("p2"));
    expect(useJournalStore.getState().subTab).toBe("apper");
    expect(navigate).not.toHaveBeenCalled();
  });

  it("replaces the URL when the sub-tab is chosen in the app", async () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync(journal("p1"), navigate));
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    act(() => useJournalStore.getState().setSubTab("apper"));
    expect(navigate).toHaveBeenCalledExactlyOnceWith({
      to: "/patients/$patientId",
      params: { patientId: "p1" },
      search: { tab: "apper" },
      replace: true,
    });
  });

  it("replaces the URL when the app switches back to Konsultasjon", async () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync(journal("p1", undefined, "apper"), navigate));
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    act(() => useJournalStore.getState().setSubTab("konsultasjon"));
    expect(navigate).toHaveBeenCalledExactlyOnceWith({
      to: "/patients/$patientId",
      params: { patientId: "p1" },
      replace: true,
    });
  });

  it("clears the journal when its tab is closed", async () => {
    renderHook(() => useRouteTabSync(journal("p1"), vi.fn()));
    await waitFor(() => expect(useJournalStore.getState().status).toBe("ready"));
    act(() => store().closeTab("journal"));
    expect(useJournalStore.getState().patientId).toBeNull();
  });
});
