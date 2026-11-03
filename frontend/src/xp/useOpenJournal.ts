import { useNavigate } from "@tanstack/react-router";
import { useJournalGuardStore } from "./journalGuardStore";
import { journalRoute } from "./tabRoutes";

export function useOpenJournal() {
  const navigate = useNavigate();
  return (patient: { id: string }) => {
    useJournalGuardStore.getState().setInAppTarget(patient.id);
    void navigate(journalRoute(patient.id));
  };
}
