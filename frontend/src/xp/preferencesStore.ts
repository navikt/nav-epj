import { create } from "zustand";

export type Theme = "luna" | "klassisk";
export type TextScale = 100 | 125 | 150;

export const THEME_KEY = "nav-epj:theme";
export const REDUCE_MOTION_KEY = "nav-epj:reduceMotion";
export const TEXT_SCALE_KEY = "nav-epj:textScale";

export const TEXT_SCALES: readonly TextScale[] = [100, 125, 150];

type Preferences = {
  theme: Theme;
  reduceMotion: boolean;
  textScale: TextScale;
};

type PreferencesState = Preferences & {
  setTheme: (theme: Theme) => void;
  setReduceMotion: (reduceMotion: boolean) => void;
  setTextScale: (textScale: TextScale) => void;
};

function read(key: string) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    return;
  }
}

export function readPreferences(): Preferences {
  const scale = Number(read(TEXT_SCALE_KEY));
  return {
    theme: read(THEME_KEY) === "klassisk" ? "klassisk" : "luna",
    reduceMotion: read(REDUCE_MOTION_KEY) === "true",
    textScale: TEXT_SCALES.find((s) => s === scale) ?? 100,
  };
}

export const usePreferencesStore = create<PreferencesState>((set) => ({
  ...readPreferences(),
  setTheme: (theme) => {
    write(THEME_KEY, theme);
    set({ theme });
  },
  setReduceMotion: (reduceMotion) => {
    write(REDUCE_MOTION_KEY, String(reduceMotion));
    set({ reduceMotion });
  },
  setTextScale: (textScale) => {
    write(TEXT_SCALE_KEY, String(textScale));
    set({ textScale });
  },
}));
