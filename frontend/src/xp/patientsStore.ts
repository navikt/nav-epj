import { create } from "zustand";
import { fetchPatients } from "./api";
import type { Pasient } from "../utils/mapping/epj";

export const PAGE_SIZE = 25;
const LEGACY_RECENT_KEY = "nav-epj:recent";
const recentKey = (owner: string) => `nav-epj:recent:${owner}`;
const RECENT_LIMIT = 10;

export type PatientsView = "mine" | "recent";

export type LastKonsultasjon = {
  status: string;
  tidspunkt: string;
};

type LoadStatus = "idle" | "loading" | "ready" | "error";

type PatientsState = {
  status: LoadStatus;
  patients: Pasient[];
  query: string;
  view: PatientsView;
  page: number;
  owner: string | null;
  recentIds: string[];
  lastKonsultasjon: Record<string, LastKonsultasjon>;
  load: () => Promise<void>;
  setQuery: (query: string) => void;
  setView: (view: PatientsView) => void;
  setPage: (page: number) => void;
  setOwner: (owner: string | null) => void;
  markOpened: (patientId: string) => void;
  setLastKonsultasjon: (patientId: string, last: LastKonsultasjon | null) => void;
  reset: () => void;
};

function readRecent(owner: string): string[] {
  try {
    const parsed: unknown = JSON.parse(
      localStorage.getItem(recentKey(owner)) ?? "[]",
    );
    return Array.isArray(parsed)
      ? parsed.filter((id): id is string => typeof id === "string")
      : [];
  } catch {
    return [];
  }
}

function persistRecent(owner: string, recentIds: string[]) {
  try {
    localStorage.setItem(recentKey(owner), JSON.stringify(recentIds));
  } catch {
    return;
  }
}

function removeLegacyRecent() {
  try {
    localStorage.removeItem(LEGACY_RECENT_KEY);
  } catch {
    return;
  }
}

function initialState() {
  return {
    status: "idle" as LoadStatus,
    patients: [] as Pasient[],
    query: "",
    view: "mine" as PatientsView,
    page: 1,
    owner: null as string | null,
    recentIds: [] as string[],
    lastKonsultasjon: {} as Record<string, LastKonsultasjon>,
  };
}

export const usePatientsStore = create<PatientsState>((set, get) => ({
  ...initialState(),
  load: async () => {
    if (get().status === "loading") return;
    set({ status: "loading" });
    try {
      set({ patients: await fetchPatients(), status: "ready" });
    } catch {
      set({ status: "error" });
    }
  },
  setQuery: (query) => set({ query, page: 1 }),
  setView: (view) => set({ view, page: 1 }),
  setPage: (page) => set({ page }),
  markOpened: (patientId) => {
    const recentIds = [
      patientId,
      ...get().recentIds.filter((id) => id !== patientId),
    ].slice(0, RECENT_LIMIT);
    const { owner } = get();
    if (owner) persistRecent(owner, recentIds);
    set({ recentIds });
  },
  setOwner: (owner) => {
    if (owner === get().owner) return;
    removeLegacyRecent();
    set({ owner, recentIds: owner ? readRecent(owner) : [] });
  },
  setLastKonsultasjon: (patientId, last) =>
    set((s) => {
      const next = { ...s.lastKonsultasjon };
      if (last) next[patientId] = last;
      else delete next[patientId];
      return { lastKonsultasjon: next };
    }),
  reset: () => set(initialState()),
}));

export function scopePatients(
  patients: Pasient[],
  recentIds: string[],
  view: PatientsView,
) {
  if (view === "mine") return patients;
  return recentIds.flatMap((id) => {
    const found = patients.find((pasient) => pasient.id === id);
    return found ? [found] : [];
  });
}
