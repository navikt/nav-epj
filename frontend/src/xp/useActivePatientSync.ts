import { useEffect } from "react";
import { useActivePatientStore } from "./activePatientStore";

export function useActivePatientSync(enabled: boolean) {
  useEffect(() => {
    if (!enabled) return;
    const { refresh } = useActivePatientStore.getState();
    const onVisible = () => {
      if (document.visibilityState === "visible") void refresh();
    };
    window.addEventListener("focus", onVisible);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.removeEventListener("focus", onVisible);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [enabled]);
}
