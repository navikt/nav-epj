import { create } from "zustand";
import type { App } from "../utils/mapping/epj";

export type AppErrorCode =
  | "NO_ACTIVE_PATIENT"
  | "NO_ACTIVE_ENCOUNTER"
  | "UNKNOWN_APP"
  | "FRAMING_REFUSED"
  | "SESSION_EXPIRED"
  | "NETWORK";

export type AppDialog =
  | {
      kind: "error";
      code: AppErrorCode;
      clientId: string | null;
      app: string;
      patientName: string;
      status: number | null;
      call: string;
      at: Date;
      retry?: () => void;
    }
  | { kind: "ask"; app: App }
  | { kind: "tabApp"; clientId: string };

type AppDialogState = {
  dialog: AppDialog | null;
  show: (dialog: AppDialog) => void;
  close: () => void;
};

export const useAppDialogStore = create<AppDialogState>((set, get) => ({
  dialog: null,
  show: (dialog) => {
    const current = get().dialog;
    if (current?.kind === "error" && current.code === "SESSION_EXPIRED") return;
    set({ dialog });
  },
  close: () => set({ dialog: null }),
}));
