import { useEffect } from "react";
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
      if (state.patientId) closeStaleRuns(state.patientId);
      syncRunTabs();
    });
    return () => {
      stopWorkspace();
      stopRuns();
      stopJournal();
    };
  }, []);
}
