import { afterEach, describe, expect, it } from "vitest";
import {
  guardTabClose,
  needsSwitchConfirmation,
  useJournalGuardStore,
} from "./journalGuardStore";
import { useJournalStore } from "./journalStore";
import type { Tab } from "./workspaceStore";

const journalTab: Tab = { id: "journal", kind: "journal", label: "Journal", closable: true };
const patientsTab: Tab = { id: "patients", kind: "patients", label: "Pasienter", closable: true };

const pasient = {
  id: "p1",
  fornavn: "Matematisk",
  etternavn: "Ape",
  personident: "01019012345",
  personidentType: "FNR" as const,
  birthDate: "1990-01-01",
  gender: "MALE" as const,
};

afterEach(() => {
  useJournalStore.getState().clear();
  useJournalGuardStore.setState({ closeRequested: false, inAppTarget: null });
});

describe("guardTabClose", () => {
  it("allows closing any tab that is not a dirty journal", () => {
    expect(guardTabClose(patientsTab)).toBe(true);
    expect(guardTabClose(journalTab)).toBe(true);
    expect(useJournalGuardStore.getState().closeRequested).toBe(false);
  });

  it("vetoes closing a dirty journal and requests confirmation", () => {
    useJournalStore.getState().setNotat("ulagret");
    expect(guardTabClose(journalTab)).toBe(false);
    expect(useJournalGuardStore.getState().closeRequested).toBe(true);
    expect(guardTabClose(patientsTab)).toBe(true);
  });
});

describe("needsSwitchConfirmation", () => {
  it("is false without a loaded journal", () => {
    expect(needsSwitchConfirmation("p2")).toBe(false);
  });

  it("is true only for a different patient than the loaded journal", () => {
    useJournalStore.setState({ patientId: "p1", patient: pasient });
    expect(needsSwitchConfirmation("p1")).toBe(false);
    expect(needsSwitchConfirmation("p2")).toBe(true);
  });
});
