import { create } from "zustand";
import {
  cancelConsultation,
  fetchConsultations,
  fetchPatient,
  saveConsultation,
  startConsultation,
} from "./api";
import { useActivePatientStore } from "./activePatientStore";
import { useBalloonStore } from "./balloonStore";
import { copy } from "./copy";
import { fullName } from "./patientInfo";
import { usePatientsStore } from "./patientsStore";
import { createSession } from "./session";
import { useWorkspaceStore } from "./workspaceStore";
import type { Consultation, Patient } from "../utils/mapping/epj";

export type DiagnosisItem = Consultation["diagnoses"][number];
export const JOURNAL_SUB_TABS = ["consultation", "previous", "maalinger", "apper"] as const;
export type JournalSubTab = (typeof JOURNAL_SUB_TABS)[number];
export type LoadStatus = "idle" | "loading" | "ready" | "error";
export type SaveStatus = "idle" | "saving" | "saved" | "error";

type Draft = { diagnoses: DiagnosisItem[]; notat: string };

type JournalState = {
  patientId: string | null;
  patient: Patient | null;
  consultations: Consultation[];
  status: LoadStatus;
  loadError: "patient" | "consultation" | null;
  selectedConsultationId: string | null;
  subTab: JournalSubTab;
  draft: Draft;
  baseline: Draft;
  draftConsultationId: string | null;
  saveStatus: SaveStatus;
  savedAt: Date | null;
  starting: boolean;
  startFailed: boolean;
  refreshing: boolean;
  refreshFailed: boolean;
  open: (patientId: string, consultationId?: string) => Promise<void>;
  refresh: () => Promise<void>;
  setSubTab: (subTab: JournalSubTab) => void;
  setNotat: (notat: string) => void;
  addDiagnosis: (diagnosis: DiagnosisItem) => void;
  removeDiagnosis: (code: string, system: string) => void;
  start: () => Promise<void>;
  save: (options?: { finalize?: boolean }) => Promise<boolean>;
  cancel: () => Promise<boolean>;
  discardDraft: () => void;
  clear: () => void;
};

const emptyDraft = (): Draft => ({ diagnoses: [], notat: "" });

function initialState() {
  return {
    patientId: null as string | null,
    patient: null as Patient | null,
    consultations: [] as Consultation[],
    status: "idle" as LoadStatus,
    loadError: null as "patient" | "consultation" | null,
    selectedConsultationId: null as string | null,
    subTab: "consultation" as JournalSubTab,
    draft: emptyDraft(),
    baseline: emptyDraft(),
    draftConsultationId: null as string | null,
    saveStatus: "idle" as SaveStatus,
    savedAt: null as Date | null,
    starting: false,
    startFailed: false,
    refreshing: false,
    refreshFailed: false,
  };
}

export const diagnosisKey = (d: { code: string; system: string }) =>
  `${d.system}:${d.code}`;

export function noteOf(consultation: Consultation) {
  return consultation.journalnotat.at(-1)?.journalnotat ?? "";
}

export function ongoingOf(consultations: Consultation[]) {
  return consultations.find((k) => k.status === "IN_PROGRESS") ?? null;
}

function draftOf(consultation: Consultation | null): Draft {
  if (!consultation) return emptyDraft();
  return {
    diagnoses: consultation.diagnoses.map((d) => ({ ...d })),
    notat: noteOf(consultation),
  };
}

function sameDraft(a: Draft, b: Draft) {
  if (a.notat !== b.notat || a.diagnoses.length !== b.diagnoses.length) {
    return false;
  }
  const keys = new Set(b.diagnoses.map(diagnosisKey));
  return a.diagnoses.every((d) => keys.has(diagnosisKey(d)));
}

export function isDirty(state: Pick<JournalState, "draft" | "baseline">) {
  return !sameDraft(state.draft, state.baseline);
}

function applySaved(
  consultations: Consultation[],
  id: string,
  draft: Draft,
  finalize: boolean,
): Consultation[] {
  return consultations.map((k) => {
    if (k.id !== id) return k;
    const existing = k.journalnotat.at(-1);
    const journalnotat =
      draft.notat.trim() === ""
        ? k.journalnotat
        : [
            {
              id: existing?.id ?? `${id}:note`,
              consultationId: id,
              patientId: k.patientId,
              journalnotat: draft.notat,
            },
          ];
    return {
      ...k,
      diagnoses: draft.diagnoses.map((d) => ({ ...d })),
      journalnotat,
      status: finalize ? "FINISHED" : k.status,
      endedAt: finalize
        ? new Date().toISOString()
        : k.endedAt,
    };
  });
}

function applyCancelled(consultations: Consultation[], id: string): Consultation[] {
  return consultations.map((k) =>
    k.id === id
      ? { ...k, status: "CANCELLED", endedAt: new Date().toISOString() }
      : k,
  );
}

function latestOf(consultations: Consultation[]) {
  return [...consultations].sort((a, b) =>
    b.startedAt.localeCompare(a.startedAt),
  )[0];
}

function publishLatest(patientId: string, consultations: Consultation[]) {
  const latest = latestOf(consultations);
  usePatientsStore.getState().setLastConsultation(
    patientId,
    latest
      ? {
          status: latest.status,
          timestamp: latest.endedAt ?? latest.startedAt,
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

  function applyConsultations(consultations: Consultation[]) {
    const ongoing = ongoingOf(consultations);
    const draft = draftOf(ongoing);
    commit({
      consultations,
      draft,
      baseline: draftOf(ongoing),
      draftConsultationId: ongoing?.id ?? null,
    });
  }

  return {
    ...initialState(),

    open: async (patientId, consultationId) => {
      const current = get();
      const selection = {
        selectedConsultationId: consultationId ?? null,
        ...(consultationId ? { subTab: "consultation" as const } : {}),
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
        subTab: current.patientId === patientId ? current.subTab : "consultation",
        ...selection,
        patientId,
        status: "loading",
      });
      const [patient, consultations, active] = await Promise.allSettled([
        fetchPatient(patientId),
        fetchConsultations(patientId),
        useActivePatientStore.getState().claim(patientId),
      ]);
      if (!isCurrent()) return;
      if (patient.status === "rejected" || active.status === "rejected") {
        commit({ status: "error", loadError: "patient" });
        return;
      }
      if (consultations.status === "rejected") {
        commit({ patient: patient.value, status: "error", loadError: "consultation" });
        return;
      }
      usePatientsStore.getState().markOpened(patientId);
      publishLatest(patientId, consultations.value);
      commit({ patient: patient.value, status: "ready", loadError: null });
      applyConsultations(consultations.value);
    },

    refresh: async () => {
      const start = get();
      const { patientId } = start;
      if (
        !patientId ||
        start.status !== "ready" ||
        start.refreshing ||
        start.saveStatus === "saving" ||
        start.starting ||
        isDirty(start)
      ) {
        return;
      }
      const isCurrent = session.capture();
      const before = start.consultations;
      commit({ refreshing: true });
      let fetched: Consultation[];
      try {
        fetched = await fetchConsultations(patientId);
      } catch {
        if (isCurrent()) commit({ refreshing: false, refreshFailed: true });
        return;
      }
      if (!isCurrent()) return;
      const now = get();
      if (
        now.saveStatus === "saving" ||
        now.starting ||
        isDirty(now) ||
        now.consultations !== before
      ) {
        commit({ refreshing: false });
        return;
      }
      publishLatest(patientId, fetched);
      commit({ refreshing: false, refreshFailed: false });
      applyConsultations(fetched);
    },

    setSubTab: (subTab) => commit({ subTab }),

    setNotat: (notat) =>
      commit({
        draft: { ...get().draft, notat },
        saveStatus: get().saveStatus === "saved" ? "idle" : get().saveStatus,
      }),

    addDiagnosis: (diagnosis) => {
      const { draft } = get();
      if (draft.diagnoses.some((d) => diagnosisKey(d) === diagnosisKey(diagnosis))) {
        return;
      }
      commit({ draft: { ...draft, diagnoses: [...draft.diagnoses, diagnosis] } });
    },

    removeDiagnosis: (code, system) => {
      const { draft } = get();
      commit({
        draft: {
          ...draft,
          diagnoses: draft.diagnoses.filter(
            (d) => diagnosisKey(d) !== diagnosisKey({ code, system }),
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
        const started = await startConsultation(patientId);
        if (!isCurrent()) return;
        const rest = get().consultations.filter((k) => k.id !== started.id);
        const consultations = [started, ...rest];
        useActivePatientStore.getState().setActive(patientId);
        publishLatest(patientId, consultations);
        commit({
          starting: false,
          selectedConsultationId: null,
          subTab: "consultation",
          saveStatus: "idle",
          savedAt: null,
        });
        applyConsultations(consultations);
      } catch {
        if (isCurrent()) {
          commit({ starting: false, startFailed: true });
        }
      }
    },

    save: async ({ finalize = false } = {}) => {
      const { patientId, patient, draft, draftConsultationId } = get();
      if (!patientId || !patient || !draftConsultationId) return false;
      if (get().saveStatus === "saving") return false;
      const isCurrent = session.capture();
      commit({ saveStatus: "saving" });
      try {
        await saveConsultation(patientId, {
          consultationId: draftConsultationId,
          diagnoses: draft.diagnoses.map((d) => ({
            code: d.code,
            system: d.system,
          })),
          journalNotat: draft.notat.trim() === "" ? null : draft.notat,
          finalize,
        });
      } catch {
        if (isCurrent()) {
          commit({ saveStatus: "error" });
        }
        return false;
      }
      let refreshed: Consultation[];
      try {
        refreshed = await fetchConsultations(patientId);
      } catch {
        refreshed = applySaved(
          get().consultations,
          draftConsultationId,
          draft,
          finalize,
        );
      }
      if (!isCurrent()) return true;
      publishLatest(patientId, refreshed);
      commit({
        saveStatus: "saved",
        savedAt: new Date(),
        ...(finalize ? { selectedConsultationId: draftConsultationId } : {}),
      });
      applyConsultations(refreshed);
      const name = fullName(patient);
      useBalloonStore.getState().show(
        finalize
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

    cancel: async () => {
      const { patientId, patient, draftConsultationId } = get();
      if (!patientId || !patient || !draftConsultationId) return false;
      if (get().saveStatus === "saving") return false;
      const isCurrent = session.capture();
      commit({ saveStatus: "saving" });
      try {
        await cancelConsultation(patientId, draftConsultationId);
      } catch {
        if (isCurrent()) {
          commit({ saveStatus: "error" });
        }
        return false;
      }
      let refreshed: Consultation[];
      try {
        refreshed = await fetchConsultations(patientId);
      } catch {
        refreshed = applyCancelled(get().consultations, draftConsultationId);
      }
      if (!isCurrent()) return true;
      publishLatest(patientId, refreshed);
      commit({ saveStatus: "idle", savedAt: null });
      applyConsultations(refreshed);
      useBalloonStore.getState().show({
        title: copy["s4.cancelDlg.balloon.title"],
        body: copy["s4.cancelDlg.balloon.body"](fullName(patient)),
      });
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
