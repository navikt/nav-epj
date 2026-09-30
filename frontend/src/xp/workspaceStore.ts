import { create } from "zustand";
import { appTabId } from "./appInfo";
import { copy } from "./copy";

export type TabKind =
  | "start"
  | "patients"
  | "journal"
  | "app"
  | "kontrollpanel"
  | "sysinfo"
  | "hjelp"
  | "hendelseslogg";

type TabBase = {
  id: string;
  label: string;
  closable: boolean;
  mark?: string;
  ariaLabel?: string;
  unsaved?: boolean;
  error?: boolean;
};

export type Tab =
  | (TabBase & { kind: "app"; clientId: string })
  | (TabBase & { kind: Exclude<TabKind, "app">; clientId?: undefined });

export type TabPatch = Partial<
  Pick<Tab, "label" | "mark" | "ariaLabel" | "unsaved" | "error">
>;

type OpenTabBase = {
  label: string;
  closable?: boolean;
  mark?: string;
  ariaLabel?: string;
  unsaved?: boolean;
  error?: boolean;
};

export type OpenTabInput =
  | (OpenTabBase & { kind: "app"; clientId: string })
  | (OpenTabBase & { kind: Exclude<TabKind, "app">; clientId?: undefined });

type WorkspaceState = {
  tabs: Tab[];
  current: string;
  openTab: (input: OpenTabInput) => string;
  closeTab: (id: string) => void;
  setCurrent: (id: string) => void;
  updateTab: (id: string, patch: TabPatch) => void;
  reset: () => void;
};

export const START_TAB_ID = "start";
export const JOURNAL_TAB_ID = "journal";
export const PATIENTS_TAB_ID = "patients";

function initialState() {
  return {
    tabs: [
      {
        id: START_TAB_ID,
        kind: "start" as const,
        label: copy["tabs.start"],
        closable: false,
      },
    ],
    current: START_TAB_ID,
  };
}

function tabId(input: OpenTabInput) {
  return input.kind === "app" ? appTabId(input.clientId) : input.kind;
}

export const useWorkspaceStore = create<WorkspaceState>((set, get) => ({
  ...initialState(),

  openTab: (input) => {
    const { tabs } = get();
    const id = tabId(input);
    const { closable = true, ...rest } = input;
    const tab: Tab = { ...rest, id, closable };
    if (!tabs.some((t) => t.id === id)) {
      set({ tabs: [...tabs, tab], current: id });
    } else if (input.kind === "journal") {
      set({ tabs: tabs.map((t) => (t.id === id ? tab : t)), current: id });
    } else {
      set({ current: id });
    }
    return id;
  },

  closeTab: (id) => {
    const { tabs, current } = get();
    const index = tabs.findIndex((t) => t.id === id);
    if (index === -1 || !tabs[index].closable) return;
    const remaining = tabs.filter((t) => t.id !== id);
    set({
      tabs: remaining,
      current: current === id ? remaining[index - 1].id : current,
    });
  },

  setCurrent: (id) => {
    if (get().tabs.some((t) => t.id === id)) set({ current: id });
  },

  updateTab: (id, patch) =>
    set({
      tabs: get().tabs.map((t) => (t.id === id ? { ...t, ...patch } : t)),
    }),

  reset: () => set(initialState()),
}));
