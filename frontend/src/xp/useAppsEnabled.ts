import { ongoingOf, useJournalStore } from "./journalStore";

export function useAppsEnabled() {
  return useJournalStore(
    (s) => s.patient !== null && ongoingOf(s.konsultasjoner) !== null,
  );
}
