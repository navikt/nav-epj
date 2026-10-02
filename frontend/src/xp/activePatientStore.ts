import { create } from "zustand";
import { broadcastActivePatient } from "./activePatientChannel";
import { fetchActivePatient, putActivePatient } from "./api";
import { createSession } from "./session";
import type { ActivePatient } from "../utils/mapping/epj";

type ActivePatientState = {
  activeId: string | null;
  setActive: (patientId: string) => void;
  claim: (patientId: string) => Promise<ActivePatient>;
  refresh: () => Promise<void>;
};

const session = createSession();

export const useActivePatientStore = create<ActivePatientState>((set) => ({
  activeId: null,
  setActive: (activeId) => {
    session.next();
    set({ activeId });
    broadcastActivePatient(activeId);
  },
  claim: async (patientId) => {
    session.next();
    const isCurrent = session.capture();
    const active = await putActivePatient(patientId);
    if (isCurrent()) {
      set({ activeId: active.patientId });
      broadcastActivePatient(active.patientId);
    }
    return active;
  },
  refresh: async () => {
    session.next();
    const isCurrent = session.capture();
    try {
      const active = await fetchActivePatient();
      if (isCurrent()) set({ activeId: active?.patientId ?? null });
    } catch {
      return;
    }
  },
}));
