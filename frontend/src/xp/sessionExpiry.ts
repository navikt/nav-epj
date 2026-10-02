import { create } from "zustand";
import { useAppDialogStore } from "./appDialogStore";
import { useAppRunStore } from "./appRunStore";

export const useSessionStore = create<{ expired: boolean }>(() => ({
  expired: false,
}));

export function expireSession(call = "") {
  if (useSessionStore.getState().expired) return;
  useSessionStore.setState({ expired: true });
  useAppRunStore.getState().markAllSession();
  useAppDialogStore.getState().show({
    kind: "error",
    code: "SESSION_EXPIRED",
    clientId: null,
    app: "",
    patientName: "",
    status: 401,
    call,
    at: new Date(),
  });
}
