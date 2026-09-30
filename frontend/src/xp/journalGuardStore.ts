import { create } from "zustand";
import { isDirty, useJournalStore } from "./journalStore";
import { JOURNAL_TAB_ID, type Tab } from "./workspaceStore";

type GuardState = {
  closeRequested: boolean;
  inAppTarget: string | null;
  requestClose: () => void;
  cancelClose: () => void;
  setInAppTarget: (patientId: string | null) => void;
};

export const useJournalGuardStore = create<GuardState>((set) => ({
  closeRequested: false,
  inAppTarget: null,
  requestClose: () => set({ closeRequested: true }),
  cancelClose: () => set({ closeRequested: false }),
  setInAppTarget: (inAppTarget) => set({ inAppTarget }),
}));

export function guardTabClose(tab: Tab) {
  if (tab.id !== JOURNAL_TAB_ID || !isDirty(useJournalStore.getState())) {
    return true;
  }
  useJournalGuardStore.getState().requestClose();
  return false;
}

export function needsSwitchConfirmation(patientId: string) {
  const { patient, patientId: openId } = useJournalStore.getState();
  return patient !== null && openId !== null && openId !== patientId;
}
