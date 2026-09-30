import { usePreferencesStore } from "./preferencesStore";
import { useMediaQuery } from "./useMediaQuery";

export function useReduceMotion() {
  const reduceMotion = usePreferencesStore((s) => s.reduceMotion);
  const setReduceMotion = usePreferencesStore((s) => s.setReduceMotion);
  const systemPrefers = useMediaQuery("(prefers-reduced-motion: reduce)");
  return {
    reduceMotion,
    setReduceMotion,
    systemPrefers,
    effective: reduceMotion || systemPrefers,
  };
}
