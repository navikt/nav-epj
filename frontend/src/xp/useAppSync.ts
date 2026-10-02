import { useEffect } from "react";
import { useActivePatientStore } from "./activePatientStore";
import { useAppRunStore } from "./appRunStore";
import { useJournalStore } from "./journalStore";
import { closeStaleRuns, dropClosedTabRuns, syncRunTabs } from "./launchApp";
import { useWorkspaceStore } from "./workspaceStore";

export function useAppSync() {
  useEffect(() => {
    const stopWorkspace = useWorkspaceStore.subscribe(dropClosedTabRuns);
    const stopRuns = useAppRunStore.subscribe(syncRunTabs);
    const stopJournal = useJournalStore.subscribe((state, previous) => {
      if (state.patientId === previous.patientId) return;
      closeStaleRuns(state.patientId);
      syncRunTabs();
    });
    const stopActive = useActivePatientStore.subscribe((state, previous) => {
      if (state.activeId !== previous.activeId) syncRunTabs();
    });
    return () => {
      stopActive();
      stopWorkspace();
      stopRuns();
      stopJournal();
    };
  }, []);
}
