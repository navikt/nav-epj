import { create } from "zustand";
import { fetchApps } from "./api";
import type { App } from "../utils/mapping/epj";

type AppsStatus = "idle" | "loading" | "ready" | "error";

type AppsState = {
  status: AppsStatus;
  apps: App[];
  load: () => Promise<void>;
  reset: () => void;
};

export const useAppsStore = create<AppsState>((set, get) => ({
  status: "idle",
  apps: [],
  load: async () => {
    if (get().status === "loading") return;
    set({ status: "loading" });
    try {
      set({ apps: await fetchApps(), status: "ready" });
    } catch {
      set({ status: "error" });
    }
  },
  reset: () => set({ status: "idle", apps: [] }),
}));
