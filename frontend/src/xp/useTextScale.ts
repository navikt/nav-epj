import { usePreferencesStore } from "./preferencesStore";

export function useTextScale() {
  const textScale = usePreferencesStore((s) => s.textScale);
  const setTextScale = usePreferencesStore((s) => s.setTextScale);
  return { textScale, setTextScale };
}
