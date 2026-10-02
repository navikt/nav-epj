import { afterEach, describe, expect, it } from "vitest";
import { useJournalStore } from "./journalStore";
import { currentJournalRoute, journalRoute, routeForTab } from "./tabRoutes";
import type { Tab } from "./workspaceStore";

const tab = (kind: Tab["kind"]): Tab =>
  (kind === "app"
    ? { id: "app:x", kind, label: "App", closable: true, clientId: "x" }
    : { id: kind, kind, label: kind, closable: true }) as Tab;

afterEach(() => useJournalStore.getState().clear());

describe("journalRoute", () => {
  it("builds the plain and the konsultasjon variant", () => {
    expect(journalRoute("p1")).toEqual({
      to: "/patients/$patientId",
      params: { patientId: "p1" },
    });
    expect(journalRoute("p1", "k1")).toEqual({
      to: "/patients/$patientId/konsultasjon/$konsultasjonId",
      params: { patientId: "p1", konsultasjonId: "k1" },
    });
    expect(journalRoute("p1", null)).toEqual(journalRoute("p1"));
  });

  it("carries a non-default sub-tab in the search and omits the default", () => {
    expect(journalRoute("p1", null, "apper")).toEqual({
      to: "/patients/$patientId",
      params: { patientId: "p1" },
      search: { tab: "apper" },
    });
    expect(journalRoute("p1", null, "konsultasjon")).toEqual(journalRoute("p1"));
  });

  it("drops the konsultasjon when another sub-tab is chosen", () => {
    expect(journalRoute("p1", "k1", "tidligere")).toEqual({
      to: "/patients/$patientId",
      params: { patientId: "p1" },
      search: { tab: "tidligere" },
    });
  });
});

describe("routeForTab", () => {
  it("maps routed tabs", () => {
    expect(routeForTab(tab("start"))).toEqual({ to: "/" });
    expect(routeForTab(tab("patients"))).toEqual({ to: "/patients" });
  });

  it("maps the journal tab to the open patient and selected konsultasjon", () => {
    expect(routeForTab(tab("journal"))).toBeNull();
    useJournalStore.setState({ patientId: "p1", selectedKonsultasjonId: "k2" });
    expect(routeForTab(tab("journal"))).toEqual(journalRoute("p1", "k2"));
    expect(currentJournalRoute()).toEqual(journalRoute("p1", "k2"));
  });

  it("keeps the sub-tab of the open journal", () => {
    useJournalStore.setState({ patientId: "p1", subTab: "apper" });
    expect(routeForTab(tab("journal"))).toEqual(journalRoute("p1", null, "apper"));
  });

  it("returns no route for tabs that are not URL-addressable", () => {
    for (const kind of ["app", "kontrollpanel", "sysinfo", "hjelp", "hendelseslogg"] as const) {
      expect(routeForTab(tab(kind))).toBeNull();
    }
  });
});
