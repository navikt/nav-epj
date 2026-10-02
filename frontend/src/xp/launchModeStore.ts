import { create } from "zustand";

export type LaunchChoice = "iframe" | "tab";

const LEGACY_KEY = "nav-epj:launchMode";
const storageKey = (owner: string) => `nav-epj:launchMode:${owner}`;

type LaunchModeState = {
  owner: string | null;
  choices: Record<string, LaunchChoice>;
  setOwner: (owner: string | null) => void;
  remember: (clientId: string, choice: LaunchChoice) => void;
  reset: () => void;
};

function read(owner: string): Record<string, LaunchChoice> {
  try {
    const parsed: unknown = JSON.parse(
      localStorage.getItem(storageKey(owner)) ?? "{}",
    );
    if (typeof parsed !== "object" || parsed === null) return {};
    return Object.fromEntries(
      Object.entries(parsed).filter(
        (entry): entry is [string, LaunchChoice] =>
          entry[1] === "iframe" || entry[1] === "tab",
      ),
    );
  } catch {
    return {};
  }
}

function persist(owner: string, choices: Record<string, LaunchChoice>) {
  try {
    localStorage.setItem(storageKey(owner), JSON.stringify(choices));
  } catch {
    return;
  }
}

function removeLegacy() {
  try {
    localStorage.removeItem(LEGACY_KEY);
  } catch {
    return;
  }
}

export const useLaunchModeStore = create<LaunchModeState>((set, get) => ({
  owner: null,
  choices: {},
  setOwner: (owner) => {
    if (owner === get().owner) return;
    removeLegacy();
    set({ owner, choices: owner ? read(owner) : {} });
  },
  remember: (clientId, choice) => {
    const choices = { ...get().choices, [clientId]: choice };
    const { owner } = get();
    if (owner) persist(owner, choices);
    set({ choices });
  },
  reset: () => set({ owner: null, choices: {} }),
}));
