import { useNavigate } from "@tanstack/react-router";
import { useJournalGuardStore } from "./journalGuardStore";
import { journalRoute } from "./tabRoutes";

export function useOpenJournal() {
  const navigate = useNavigate();
  return (pasient: { id: string }) => {
    useJournalGuardStore.getState().setInAppTarget(pasient.id);
    void navigate(journalRoute(pasient.id));
  };
}
