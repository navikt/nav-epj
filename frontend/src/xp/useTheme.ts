import { usePreferencesStore } from "./preferencesStore";

export function useTheme() {
  const theme = usePreferencesStore((s) => s.theme);
  const setTheme = usePreferencesStore((s) => s.setTheme);
  return {
    theme,
    setTheme,
    toggleTheme: () => setTheme(theme === "luna" ? "klassisk" : "luna"),
  };
}
