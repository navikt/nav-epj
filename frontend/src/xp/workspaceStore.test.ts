import { beforeEach, describe, expect, it } from "vitest";
import { START_TAB_ID, useWorkspaceStore } from "./workspaceStore";
import { copy } from "./copy";

const state = () => useWorkspaceStore.getState();

describe("workspaceStore", () => {
  beforeEach(() => state().reset());

  it("is seeded with a pinned Start tab that is current", () => {
    expect(state().tabs).toEqual([
      {
        id: START_TAB_ID,
        kind: "start",
        label: copy["tabs.start"],
        closable: false,
      },
    ]);
    expect(state().current).toBe(START_TAB_ID);
  });

  it("never closes the Start tab", () => {
    state().closeTab(START_TAB_ID);
    expect(state().tabs.map((t) => t.id)).toEqual([START_TAB_ID]);
  });

  it("opens a tab and makes it current", () => {
    const id = state().openTab({ kind: "patients", label: "Pasienter" });
    expect(state().tabs.map((t) => t.id)).toEqual([START_TAB_ID, id]);
    expect(state().current).toBe(id);
  });

  it("keeps a single journal tab and replaces it when opened again", () => {
    state().openTab({ kind: "journal", label: "Journal · Ola" });
    state().openTab({ kind: "patients", label: "Pasienter" });
    state().openTab({ kind: "journal", label: "Journal · Kari" });

    const journals = state().tabs.filter((t) => t.kind === "journal");
    expect(journals).toHaveLength(1);
    expect(journals[0].label).toBe("Journal · Kari");
    expect(state().current).toBe(journals[0].id);
    expect(state().tabs).toHaveLength(3);
  });

  it("keeps one tab per app clientId and activates the existing one", () => {
    const first = state().openTab({
      kind: "app",
      clientId: "syk-inn",
      label: "Sykmelding · MA",
    });
    state().openTab({ kind: "patients", label: "Pasienter" });
    const again = state().openTab({
      kind: "app",
      clientId: "syk-inn",
      label: "Sykmelding · MA",
    });

    expect(again).toBe(first);
    expect(state().tabs.filter((t) => t.kind === "app")).toHaveLength(1);
    expect(state().current).toBe(first);
  });

  it("allows different apps side by side", () => {
    state().openTab({ kind: "app", clientId: "syk-inn", label: "Sykmelding" });
    state().openTab({ kind: "app", clientId: "validator", label: "Validator" });
    expect(state().tabs.filter((t) => t.kind === "app")).toHaveLength(2);
  });

  it("does not duplicate singleton tabs such as Kontrollpanel", () => {
    state().openTab({ kind: "kontrollpanel", label: "Kontrollpanel" });
    state().openTab({ kind: "kontrollpanel", label: "Kontrollpanel" });
    expect(state().tabs.filter((t) => t.kind === "kontrollpanel")).toHaveLength(1);
  });

  it("selects the tab to the left when the current tab is closed", () => {
    const patients = state().openTab({ kind: "patients", label: "Pasienter" });
    const journal = state().openTab({ kind: "journal", label: "Journal" });
    state().closeTab(journal);
    expect(state().current).toBe(patients);
    state().closeTab(patients);
    expect(state().current).toBe(START_TAB_ID);
  });

  it("keeps the current tab when another tab is closed", () => {
    const patients = state().openTab({ kind: "patients", label: "Pasienter" });
    const journal = state().openTab({ kind: "journal", label: "Journal" });
    state().closeTab(patients);
    expect(state().current).toBe(journal);
  });

  it("ignores setCurrent for unknown tabs", () => {
    state().setCurrent("finnes-ikke");
    expect(state().current).toBe(START_TAB_ID);
  });

  it("does not close tabs opened as not closable", () => {
    const id = state().openTab({
      kind: "hjelp",
      label: "Hjelp",
      closable: false,
    });
    state().closeTab(id);
    expect(state().tabs.map((t) => t.id)).toContain(id);
  });
});
