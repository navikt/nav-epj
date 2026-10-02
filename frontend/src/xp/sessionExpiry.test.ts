import { describe, expect, it } from "vitest";
import { seedRun } from "./appFixtures";
import { useAppDialogStore } from "./appDialogStore";
import { useAppRunStore } from "./appRunStore";
import { expireSession, useSessionStore } from "./sessionExpiry";

describe("expireSession", () => {
  it("opens the dialog once and flags every running app", () => {
    seedRun();
    expireSession("GET /api/patient");
    expect(useSessionStore.getState().expired).toBe(true);
    expect(useAppRunStore.getState().runs[0].status).toBe("session");
    const first = useAppDialogStore.getState().dialog;
    expireSession("GET /api/other");
    expect(useAppDialogStore.getState().dialog).toBe(first);
  });

  it("is not replaced by later dialogs", () => {
    expireSession();
    useAppDialogStore.getState().show({
      kind: "error",
      code: "NETWORK",
      clientId: null,
      app: "",
      patientName: "",
      status: null,
      call: "",
      at: new Date(),
    });
    expect(useAppDialogStore.getState().dialog).toMatchObject({
      code: "SESSION_EXPIRED",
    });
  });
});
