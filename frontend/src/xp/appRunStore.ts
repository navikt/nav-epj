import { create } from "zustand";
import type { Konsultasjon, Pasient } from "../utils/mapping/epj";

export type RunStatus = "starting" | "running" | "timeout" | "error" | "session";

export type RunEventInput =
  | { kind: "launch"; status: number }
  | { kind: "load"; url: string };

export type RunEvent = RunEventInput & { at: Date };

export type AppRun = {
  clientId: string;
  navn: string;
  patient: Pasient;
  konsultasjon: Pick<Konsultasjon, "id" | "startetTidspunkt">;
  launchUrl: string | null;
  status: RunStatus;
  startedAt: Date;
  events: RunEvent[];
  attempt: number;
};

export type TabApp = {
  clientId: string;
  navn: string;
  patient: Pasient;
  startedAt: Date;
};

type AppRunState = {
  runs: AppRun[];
  tabApps: TabApp[];
  startRun: (run: Omit<AppRun, "status" | "events" | "attempt" | "launchUrl">) => void;
  restartRun: (clientId: string, startedAt: Date) => void;
  setLaunchUrl: (clientId: string, launchUrl: string) => void;
  setStatus: (clientId: string, status: RunStatus) => void;
  addEvent: (clientId: string, event: RunEventInput) => void;
  removeRun: (clientId: string) => void;
  markAllSession: () => void;
  addTabApp: (app: TabApp) => void;
  removeTabApp: (clientId: string) => void;
  reset: () => void;
};

function patchRun(
  runs: AppRun[],
  clientId: string,
  patch: (run: AppRun) => AppRun,
) {
  return runs.map((run) => (run.clientId === clientId ? patch(run) : run));
}

export const useAppRunStore = create<AppRunState>((set) => ({
  runs: [],
  tabApps: [],
  startRun: (run) =>
    set((s) => ({
      runs: [
        ...s.runs.filter((r) => r.clientId !== run.clientId),
        { ...run, launchUrl: null, status: "starting", events: [], attempt: 0 },
      ],
    })),
  restartRun: (clientId, startedAt) =>
    set((s) => ({
      runs: patchRun(s.runs, clientId, (run) => ({
        ...run,
        launchUrl: null,
        status: "starting",
        startedAt,
        attempt: run.attempt + 1,
      })),
    })),
  setLaunchUrl: (clientId, launchUrl) =>
    set((s) => ({
      runs: patchRun(s.runs, clientId, (run) => ({ ...run, launchUrl })),
    })),
  setStatus: (clientId, status) =>
    set((s) => ({
      runs: patchRun(s.runs, clientId, (run) => ({ ...run, status })),
    })),
  addEvent: (clientId, event) =>
    set((s) => ({
      runs: patchRun(s.runs, clientId, (run) => ({
        ...run,
        events: [...run.events, { ...event, at: new Date() }],
      })),
    })),
  removeRun: (clientId) =>
    set((s) => ({ runs: s.runs.filter((r) => r.clientId !== clientId) })),
  markAllSession: () =>
    set((s) => ({ runs: s.runs.map((run) => ({ ...run, status: "session" })) })),
  addTabApp: (app) =>
    set((s) => ({
      tabApps: [...s.tabApps.filter((a) => a.clientId !== app.clientId), app],
    })),
  removeTabApp: (clientId) =>
    set((s) => ({ tabApps: s.tabApps.filter((a) => a.clientId !== clientId) })),
  reset: () => set({ runs: [], tabApps: [] }),
}));

export const APP_ACCESS_MS = 60 * 60 * 1000;

export const accessExpiry = (startedAt: Date) =>
  new Date(startedAt.getTime() + APP_ACCESS_MS);

export function isStaleFor(
  ownerPatientId: string,
  journalPatientId: string | null,
) {
  return journalPatientId !== null && journalPatientId !== ownerPatientId;
}
