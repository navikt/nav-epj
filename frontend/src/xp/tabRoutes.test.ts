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

  it("returns no route for tabs that are not URL-addressable", () => {
    for (const kind of ["app", "kontrollpanel", "sysinfo", "hjelp", "hendelseslogg"] as const) {
      expect(routeForTab(tab(kind))).toBeNull();
    }
  });
});
