import { useNavigate } from "@tanstack/react-router";
import { filterPatients } from "./patientFilter";
import { scopePatients, usePatientsStore } from "./patientsStore";
import { useOpenJournal } from "./useOpenJournal";

export function useSearchSubmit() {
  const navigate = useNavigate();
  const openJournal = useOpenJournal();
  return async () => {
    const store = usePatientsStore.getState();
    if (store.query.trim() !== "") {
      if (store.status === "idle" || store.status === "error") {
        await store.load();
      }
      const { status, patients, recentIds, view, query } =
        usePatientsStore.getState();
      const matches =
        status === "ready"
          ? filterPatients(scopePatients(patients, recentIds, view), query)
          : [];
      if (matches.length === 1) {
        usePatientsStore.getState().setQuery("");
        openJournal(matches[0]);
        return;
      }
    }
    void navigate({ to: "/patients" });
  };
}
