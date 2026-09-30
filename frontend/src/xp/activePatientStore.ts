import { create } from "zustand";
import { fetchActivePatient } from "./api";
import { createSession } from "./session";

type ActivePatientState = {
  activeId: string | null;
  setActive: (patientId: string | null) => void;
  refresh: () => Promise<void>;
};

const session = createSession();

export const useActivePatientStore = create<ActivePatientState>((set) => ({
  activeId: null,
  setActive: (activeId) => {
    session.next();
    set({ activeId });
  },
  refresh: async () => {
    const isCurrent = session.capture();
    try {
      const active = await fetchActivePatient();
      if (isCurrent()) set({ activeId: active?.patientId ?? null });
    } catch {
      return;
    }
  },
}));
