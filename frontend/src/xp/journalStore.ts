import { create } from "zustand";
import {
  fetchKonsultasjoner,
  fetchPatient,
  putActivePatient,
  saveKonsultasjon,
  startKonsultasjon,
} from "./api";
import { useBalloonStore } from "./balloonStore";
import { copy } from "./copy";
import { fullName } from "./patientInfo";
import { usePatientsStore } from "./patientsStore";
import { createSession } from "./session";
import { useWorkspaceStore } from "./workspaceStore";
import type { Konsultasjon, Pasient } from "../utils/mapping/epj";

export type DiagnoseItem = Konsultasjon["diagnoser"][number];
export type JournalSubTab = "konsultasjon" | "tidligere" | "apper";
export type LoadStatus = "idle" | "loading" | "ready" | "error";
export type SaveStatus = "idle" | "saving" | "saved" | "error";

type Draft = { diagnoser: DiagnoseItem[]; notat: string };

type JournalState = {
  patientId: string | null;
  patient: Pasient | null;
  konsultasjoner: Konsultasjon[];
  status: LoadStatus;
  loadError: "patient" | "kons" | null;
  selectedKonsultasjonId: string | null;
  subTab: JournalSubTab;
  draft: Draft;
  baseline: Draft;
  draftKonsultasjonId: string | null;
  saveStatus: SaveStatus;
  savedAt: Date | null;
  starting: boolean;
  startFailed: boolean;
  open: (patientId: string, konsultasjonId?: string) => Promise<void>;
  setSubTab: (subTab: JournalSubTab) => void;
  setNotat: (notat: string) => void;
  addDiagnose: (diagnose: DiagnoseItem) => void;
  removeDiagnose: (code: string, system: string) => void;
  start: () => Promise<void>;
  save: (options?: { ferdigstill?: boolean }) => Promise<boolean>;
  discardDraft: () => void;
  clear: () => void;
};

const emptyDraft = (): Draft => ({ diagnoser: [], notat: "" });

function initialState() {
  return {
    patientId: null as string | null,
    patient: null as Pasient | null,
    konsultasjoner: [] as Konsultasjon[],
    status: "idle" as LoadStatus,
    loadError: null as "patient" | "kons" | null,
    selectedKonsultasjonId: null as string | null,
    subTab: "konsultasjon" as JournalSubTab,
    draft: emptyDraft(),
    baseline: emptyDraft(),
    draftKonsultasjonId: null as string | null,
    saveStatus: "idle" as SaveStatus,
    savedAt: null as Date | null,
    starting: false,
    startFailed: false,
  };
}

export const diagnoseKey = (d: { code: string; system: string }) =>
  `${d.system}:${d.code}`;

export function noteOf(konsultasjon: Konsultasjon) {
  return konsultasjon.journalnotat.at(-1)?.journalnotat ?? "";
}

export function ongoingOf(konsultasjoner: Konsultasjon[]) {
  return konsultasjoner.find((k) => k.status === "PÅGÅENDE") ?? null;
}

function draftOf(konsultasjon: Konsultasjon | null): Draft {
  if (!konsultasjon) return emptyDraft();
  return {
    diagnoser: konsultasjon.diagnoser.map((d) => ({ ...d })),
    notat: noteOf(konsultasjon),
  };
}

function sameDraft(a: Draft, b: Draft) {
  if (a.notat !== b.notat || a.diagnoser.length !== b.diagnoser.length) {
    return false;
  }
  const keys = new Set(b.diagnoser.map(diagnoseKey));
  return a.diagnoser.every((d) => keys.has(diagnoseKey(d)));
}

export function isDirty(state: Pick<JournalState, "draft" | "baseline">) {
  return !sameDraft(state.draft, state.baseline);
}

function applySaved(
  konsultasjoner: Konsultasjon[],
  id: string,
  draft: Draft,
  ferdigstill: boolean,
): Konsultasjon[] {
  return konsultasjoner.map((k) => {
    if (k.id !== id) return k;
    const existing = k.journalnotat.at(-1);
    const journalnotat =
      draft.notat.trim() === ""
        ? k.journalnotat
        : [
            {
              id: existing?.id ?? `${id}:note`,
              konsultasjonId: id,
              pasientId: k.pasientId,
              journalnotat: draft.notat,
            },
          ];
    return {
      ...k,
      diagnoser: draft.diagnoser.map((d) => ({ ...d })),
      journalnotat,
      status: ferdigstill ? "FULLFØRT" : k.status,
      avsluttetTidspunkt: ferdigstill
        ? new Date().toISOString()
        : k.avsluttetTidspunkt,
    };
  });
}

function latestOf(konsultasjoner: Konsultasjon[]) {
  return [...konsultasjoner].sort((a, b) =>
    b.startetTidspunkt.localeCompare(a.startetTidspunkt),
  )[0];
}

function publishLatest(patientId: string, konsultasjoner: Konsultasjon[]) {
  const latest = latestOf(konsultasjoner);
  usePatientsStore.getState().setLastKonsultasjon(
    patientId,
    latest
      ? {
          status: latest.status,
          tidspunkt: latest.avsluttetTidspunkt ?? latest.startetTidspunkt,
        }
      : null,
  );
}

const session = createSession();

export const useJournalStore = create<JournalState>((set, get) => {
  function commit(partial: Partial<JournalState>) {
    set(partial);
    const state = get();
    useWorkspaceStore.getState().updateTab("journal", {
      label: state.patient
        ? copy["tabs.journal"](fullName(state.patient))
        : copy["s4.tabs.label"],
      unsaved: isDirty(state),
      error: state.saveStatus === "error",
    });
  }

  function applyKonsultasjoner(konsultasjoner: Konsultasjon[]) {
    const ongoing = ongoingOf(konsultasjoner);
    const draft = draftOf(ongoing);
    commit({
      konsultasjoner,
      draft,
      baseline: draftOf(ongoing),
      draftKonsultasjonId: ongoing?.id ?? null,
    });
  }

  return {
    ...initialState(),

    open: async (patientId, konsultasjonId) => {
      const current = get();
      const selection = {
        selectedKonsultasjonId: konsultasjonId ?? null,
        ...(konsultasjonId ? { subTab: "konsultasjon" as const } : {}),
      };
      if (
        current.patientId === patientId &&
        current.status !== "error"
      ) {
        commit(selection);
        return;
      }
      session.next();
      const isCurrent = session.capture();
      commit({
        ...initialState(),
        ...selection,
        patientId,
        status: "loading",
      });
      const [patient, konsultasjoner, active] = await Promise.allSettled([
        fetchPatient(patientId),
        fetchKonsultasjoner(patientId),
        putActivePatient(patientId),
      ]);
      if (!isCurrent()) return;
      if (patient.status === "rejected" || active.status === "rejected") {
        commit({ status: "error", loadError: "patient" });
        return;
      }
      if (konsultasjoner.status === "rejected") {
        commit({ patient: patient.value, status: "error", loadError: "kons" });
        return;
      }
      usePatientsStore.getState().markOpened(patientId);
      publishLatest(patientId, konsultasjoner.value);
      commit({ patient: patient.value, status: "ready", loadError: null });
      applyKonsultasjoner(konsultasjoner.value);
    },

    setSubTab: (subTab) => commit({ subTab }),

    setNotat: (notat) =>
      commit({
        draft: { ...get().draft, notat },
        saveStatus: get().saveStatus === "saved" ? "idle" : get().saveStatus,
      }),

    addDiagnose: (diagnose) => {
      const { draft } = get();
      if (draft.diagnoser.some((d) => diagnoseKey(d) === diagnoseKey(diagnose))) {
        return;
      }
      commit({ draft: { ...draft, diagnoser: [...draft.diagnoser, diagnose] } });
    },

    removeDiagnose: (code, system) => {
      const { draft } = get();
      commit({
        draft: {
          ...draft,
          diagnoser: draft.diagnoser.filter(
            (d) => diagnoseKey(d) !== diagnoseKey({ code, system }),
          ),
        },
      });
    },

    start: async () => {
      const { patientId } = get();
      if (!patientId || get().starting) return;
      const isCurrent = session.capture();
      commit({ starting: true, startFailed: false });
      try {
        const started = await startKonsultasjon(patientId);
        if (!isCurrent()) return;
        const rest = get().konsultasjoner.filter((k) => k.id !== started.id);
        const konsultasjoner = [started, ...rest];
        publishLatest(patientId, konsultasjoner);
        commit({
          starting: false,
          selectedKonsultasjonId: null,
          subTab: "konsultasjon",
          saveStatus: "idle",
          savedAt: null,
        });
        applyKonsultasjoner(konsultasjoner);
      } catch {
        if (isCurrent()) {
          commit({ starting: false, startFailed: true });
        }
      }
    },

    save: async ({ ferdigstill = false } = {}) => {
      const { patientId, patient, draft, draftKonsultasjonId } = get();
      if (!patientId || !patient || !draftKonsultasjonId) return false;
      if (get().saveStatus === "saving") return false;
      const isCurrent = session.capture();
      commit({ saveStatus: "saving" });
      try {
        await saveKonsultasjon(patientId, {
          konsultasjonId: draftKonsultasjonId,
          diagnoser: draft.diagnoser.map((d) => ({
            kode: d.code,
            system: d.system,
          })),
          journalNotat: draft.notat.trim() === "" ? null : draft.notat,
          ferdigstill,
        });
      } catch {
        if (isCurrent()) {
          commit({ saveStatus: "error" });
        }
        return false;
      }
      let refreshed: Konsultasjon[];
      try {
        refreshed = await fetchKonsultasjoner(patientId);
      } catch {
        refreshed = applySaved(
          get().konsultasjoner,
          draftKonsultasjonId,
          draft,
          ferdigstill,
        );
      }
      if (!isCurrent()) return true;
      publishLatest(patientId, refreshed);
      commit({
        saveStatus: "saved",
        savedAt: new Date(),
        ...(ferdigstill ? { selectedKonsultasjonId: draftKonsultasjonId } : {}),
      });
      applyKonsultasjoner(refreshed);
      const name = fullName(patient);
      useBalloonStore.getState().show(
        ferdigstill
          ? {
              title: copy["s4.done.balloon.title"],
              body: copy["s4.done.balloon.body"](name),
            }
          : {
              title: copy["s4.saved.title"],
              body: copy["s4.saved.body"](name),
            },
      );
      return true;
    },

    discardDraft: () => {
      commit({ draft: { ...get().baseline }, saveStatus: "idle" });
    },

    clear: () => {
      session.next();
      set(initialState());
    },
  };
});
