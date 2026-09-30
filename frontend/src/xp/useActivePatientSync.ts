import { useEffect } from "react";
import { listenForActivePatient } from "./activePatientChannel";
import { useActivePatientStore } from "./activePatientStore";
import { useAppRunStore } from "./appRunStore";

export const ACTIVE_PATIENT_POLL_MS = 15000;

export function useActivePatientSync(enabled: boolean) {
  const hasRuns = useAppRunStore((s) => s.runs.length > 0);

  useEffect(() => {
    if (!enabled) return;
    const { refresh } = useActivePatientStore.getState();
    const onVisible = () => {
      if (document.visibilityState === "visible") void refresh();
    };
    window.addEventListener("focus", onVisible);
    document.addEventListener("visibilitychange", onVisible);
    const stopListening = listenForActivePatient(() => void refresh());
    return () => {
      window.removeEventListener("focus", onVisible);
      document.removeEventListener("visibilitychange", onVisible);
      stopListening();
    };
  }, [enabled]);

  useEffect(() => {
    if (!enabled || !hasRuns) return;
    const timer = setInterval(() => {
      if (document.visibilityState === "visible") {
        void useActivePatientStore.getState().refresh();
      }
    }, ACTIVE_PATIENT_POLL_MS);
    return () => clearInterval(timer);
  }, [enabled, hasRuns]);
}
