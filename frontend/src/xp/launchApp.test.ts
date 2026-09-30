import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  kari,
  ongoingKonsultasjon,
  launchOk,
  nyFane,
  ola,
  seedApps,
  seedJournal,
  stubLaunch,
  sykInn,
  validator,
} from "./appFixtures";
import { useAppDialogStore } from "./appDialogStore";
import { useAppRunStore } from "./appRunStore";
import { useBalloonStore } from "./balloonStore";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import {
  closeApp,
  closeStaleRuns,
  dropClosedTabRuns,
  popOutApp,
  reloadApp,
  reportTimeout,
  startApp,
  syncRunTabs,
} from "./launchApp";
import { useLaunchModeStore } from "./launchModeStore";
import { useWorkspaceStore } from "./workspaceStore";

const errorDialog = () => {
  const dialog = useAppDialogStore.getState().dialog;
  if (dialog?.kind !== "error") throw new Error("expected an error dialog");
  return dialog;
};

describe("startApp", () => {
  let open: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    open = vi.fn().mockReturnValue({});
    vi.stubGlobal("open", open);
    seedApps([sykInn, validator, nyFane]);
    seedJournal();
  });

  afterEach(() => vi.unstubAllGlobals());

  it("shows NO_ACTIVE_PATIENT without calling the backend when no journal is open", async () => {
    const fetch = stubLaunch([launchOk()]);
    useJournalStore.getState().clear();
    await startApp(sykInn);
    expect(errorDialog().code).toBe("NO_ACTIVE_PATIENT");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("shows NO_ACTIVE_ENCOUNTER without calling the backend when nothing is ongoing", async () => {
    const fetch = stubLaunch([launchOk()]);
    seedJournal(ola, []);
    await startApp(sykInn);
    expect(errorDialog()).toMatchObject({
      code: "NO_ACTIVE_ENCOUNTER",
      patientName: "Ola Nordmann",
    });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("opens an app tab in the starting state, then attaches the launch url", async () => {
    const fetch = stubLaunch([launchOk()]);
    const started = startApp(sykInn);
    const [run] = useAppRunStore.getState().runs;
    expect(run).toMatchObject({ clientId: "syk-inn", status: "starting", launchUrl: null });
    expect(useWorkspaceStore.getState().current).toBe("app:syk-inn");
    expect(await started).toBe("iframe");
    expect(fetch).toHaveBeenCalledWith(
      "/api/launch",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ appId: "syk-inn", patientId: "p1" }),
      }),
    );
    const [after] = useAppRunStore.getState().runs;
    expect(after.launchUrl).toContain("launch=id-1");
    expect(after.events).toMatchObject([{ kind: "launch", status: 200 }]);
    expect(open).not.toHaveBeenCalled();
  });

  it("focuses the existing tab instead of launching twice", async () => {
    const fetch = stubLaunch([launchOk()]);
    await startApp(sykInn);
    useWorkspaceStore.getState().setCurrent("start");
    await startApp(sykInn);
    expect(fetch).toHaveBeenCalledOnce();
    expect(useWorkspaceStore.getState().current).toBe("app:syk-inn");
  });

  it("opens browser-tab apps with noopener and records them", async () => {
    stubLaunch([launchOk()]);
    expect(await startApp(nyFane)).toBe("tab");
    expect(open).toHaveBeenCalledWith(
      expect.stringContaining("launch=id-1"),
      expect.stringMatching(/^smart-ny-fane-\d+$/),
      "noopener,noreferrer",
    );
    expect(useAppRunStore.getState().tabApps).toMatchObject([
      { clientId: "ny-fane", navn: "Fanen" },
    ]);
    expect(useAppRunStore.getState().runs).toEqual([]);
    expect(useBalloonStore.getState().balloon?.body).toBe(
      copy["s5.tab.balloon.body"]("Ola Nordmann"),
    );
  });

  it("adds the popup hint to the balloon when the browser gives no window", async () => {
    open.mockReturnValue(null);
    stubLaunch([launchOk()]);
    await startApp(nyFane);
    expect(useBalloonStore.getState().balloon?.body).toBe(
      `${copy["s5.tab.balloon.body"]("Ola Nordmann")} ${copy["s5.tab.balloon.blocked"]}`,
    );
  });

  it("keeps a tab app per launch so an older patient's tab is not overwritten", async () => {
    stubLaunch([launchOk(1), launchOk(2)]);
    await startApp(nyFane);
    seedJournal(kari, [{ ...ongoingKonsultasjon, id: "k2", pasientId: "p2" }]);
    await startApp(nyFane);
    const { tabApps } = useAppRunStore.getState();
    expect(tabApps.map((a) => a.patient.id)).toEqual(["p1", "p2"]);
    expect(new Set(tabApps.map((a) => a.id)).size).toBe(2);
    expect(open.mock.calls[0][1]).not.toBe(open.mock.calls[1][1]);
    expect(open.mock.calls.map((c) => c[1])).toEqual(tabApps.map((a) => a.id));
  });

  it("asks which view to use for ask-mode apps", async () => {
    const fetch = stubLaunch([launchOk()]);
    await startApp(validator);
    expect(useAppDialogStore.getState().dialog).toMatchObject({ kind: "ask" });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("uses the remembered choice for ask-mode apps", async () => {
    stubLaunch([launchOk()]);
    useLaunchModeStore.getState().setOwner("9144889");
    useLaunchModeStore.getState().remember("validator", "tab");
    await startApp(validator);
    expect(open).toHaveBeenCalledOnce();
    expect(useAppDialogStore.getState().dialog).toBeNull();
  });

  it("uses an explicit choice over the registered mode", async () => {
    stubLaunch([launchOk()]);
    await startApp(validator, "iframe");
    expect(useAppRunStore.getState().runs).toHaveLength(1);
  });

  it.each([
    [409, "NO_ACTIVE_PATIENT"],
    [409, "NO_ACTIVE_ENCOUNTER"],
    [409, "PATIENT_MISMATCH"],
    [404, "UNKNOWN_APP"],
  ] as const)("maps %s %s from the backend and closes the app tab", async (status, code) => {
    stubLaunch([{ status, body: { code, message: "x", appId: "syk-inn" } }]);
    await startApp(sykInn);
    expect(errorDialog()).toMatchObject({ code, status, call: "POST /api/launch" });
    expect(useAppRunStore.getState().runs).toEqual([]);
    expect(useWorkspaceStore.getState().tabs.map((t) => t.id)).toEqual(["start"]);
  });

  it("reports a network failure with a retry", async () => {
    const fetch = stubLaunch(["network", launchOk()]);
    await startApp(sykInn);
    const dialog = errorDialog();
    expect(dialog).toMatchObject({ code: "NETWORK", status: null });
    dialog.retry?.();
    await vi.waitFor(() =>
      expect(useAppRunStore.getState().runs[0]?.launchUrl).not.toBeNull(),
    );
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("leaves browser-tab failures without a tab or record", async () => {
    stubLaunch([{ status: 409, body: { code: "NO_ACTIVE_ENCOUNTER", message: "x" } }]);
    await startApp(nyFane);
    expect(errorDialog().code).toBe("NO_ACTIVE_ENCOUNTER");
    expect(useAppRunStore.getState().tabApps).toEqual([]);
    expect(open).not.toHaveBeenCalled();
  });

  it("shows only the session-expired dialog for a 401", async () => {
    stubLaunch([{ status: 401, body: {} }]);
    await startApp(sykInn);
    expect(errorDialog()).toMatchObject({ code: "SESSION_EXPIRED", status: 401 });
  });

  it("discards a launch that resolves after the patient changed", async () => {
    let resolve: (value: unknown) => void = () => {};
    vi.stubGlobal(
      "fetch",
      vi.fn(
        () =>
          new Promise((r) => {
            resolve = r;
          }),
      ),
    );
    const started = startApp(nyFane);
    seedJournal(kari);
    resolve({ ok: true, status: 200, json: async () => launchOk().body });
    expect(await started).toBeNull();
    expect(open).not.toHaveBeenCalled();
    expect(useAppRunStore.getState().tabApps).toEqual([]);
  });

  it("removes an embedded run whose launch resolves for another patient", async () => {
    let resolve: (value: unknown) => void = () => {};
    vi.stubGlobal(
      "fetch",
      vi.fn(
        () =>
          new Promise((r) => {
            resolve = r;
          }),
      ),
    );
    const started = startApp(sykInn);
    seedJournal(kari);
    resolve({ ok: true, status: 200, json: async () => launchOk().body });
    expect(await started).toBeNull();
    expect(useAppRunStore.getState().runs).toEqual([]);
    expect(useWorkspaceStore.getState().tabs.map((t) => t.id)).toEqual(["start"]);
  });

  it("ignores the result of a launch that was closed while in flight", async () => {
    let resolve: (value: unknown) => void = () => {};
    vi.stubGlobal(
      "fetch",
      vi.fn(
        () =>
          new Promise((r) => {
            resolve = r;
          }),
      ),
    );
    const started = startApp(sykInn);
    closeApp("syk-inn");
    resolve({ ok: true, status: 200, json: async () => launchOk().body });
    expect(await started).toBeNull();
    expect(useAppRunStore.getState().runs).toEqual([]);
  });
});

describe("running apps", () => {
  beforeEach(async () => {
    vi.stubGlobal("open", vi.fn().mockReturnValue({}));
    seedApps([sykInn, nyFane]);
    seedJournal();
    stubLaunch([launchOk(1)]);
    await startApp(sykInn);
  });

  afterEach(() => vi.unstubAllGlobals());

  it("relaunches with a fresh launch url and bumps the attempt", async () => {
    stubLaunch([launchOk(2)]);
    const before = useAppRunStore.getState().runs[0];
    const reload = reloadApp("syk-inn");
    expect(useAppRunStore.getState().runs[0]).toMatchObject({
      launchUrl: null,
      status: "starting",
      attempt: before.attempt + 1,
    });
    await reload;
    expect(useAppRunStore.getState().runs[0].launchUrl).toContain("launch=id-2");
  });

  it("marks the run as failed when a relaunch is rejected", async () => {
    stubLaunch([{ status: 409, body: { code: "NO_ACTIVE_ENCOUNTER", message: "x" } }]);
    await reloadApp("syk-inn");
    expect(useAppRunStore.getState().runs[0].status).toBe("error");
    expect(errorDialog().code).toBe("NO_ACTIVE_ENCOUNTER");
  });

  it("does not relaunch for a patient that is no longer open", async () => {
    const fetch = stubLaunch([launchOk(2)]);
    seedJournal(kari);
    await reloadApp("syk-inn");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("pops out into a browser tab and closes the app tab", async () => {
    stubLaunch([launchOk(3)]);
    expect(await popOutApp("syk-inn")).toBe("tab");
    expect(window.open).toHaveBeenCalledOnce();
    expect(useAppRunStore.getState().runs).toEqual([]);
    expect(useWorkspaceStore.getState().tabs.map((t) => t.id)).toEqual(["start"]);
    expect(useAppRunStore.getState().tabApps).toHaveLength(1);
    expect(useBalloonStore.getState().balloon?.body).toBe(
      copy["s5.popout.balloon.body"],
    );
  });

  it("does not close or relaunch when the journal was closed before the pop-out", async () => {
    const fetch = stubLaunch([launchOk(3)]);
    useJournalStore.getState().clear();
    expect(await popOutApp("syk-inn")).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
    expect(useAppRunStore.getState().runs).toHaveLength(1);
  });

  it("ignores a timeout that arrives after the app has loaded", () => {
    useAppRunStore.getState().setStatus("syk-inn", "running");
    reportTimeout("syk-inn", "https://syk.example");
    expect(useAppRunStore.getState().runs[0].status).toBe("running");
    expect(useAppDialogStore.getState().dialog).toBeNull();
  });

  it("closes every embedded run when no journal is open", () => {
    closeStaleRuns(null);
    expect(useAppRunStore.getState().runs).toEqual([]);
  });

  it("reports the 8 second timeout as a run status and an S8 dialog", () => {
    reportTimeout("syk-inn", "https://syk.example");
    expect(useAppRunStore.getState().runs[0].status).toBe("timeout");
    expect(errorDialog()).toMatchObject({
      code: "FRAMING_REFUSED",
      clientId: "syk-inn",
      call: "https://syk.example",
    });
  });

  it("closes embedded runs that belong to another patient", () => {
    closeStaleRuns("p1");
    expect(useAppRunStore.getState().runs).toHaveLength(1);
    closeStaleRuns("p2");
    expect(useAppRunStore.getState().runs).toEqual([]);
    expect(useWorkspaceStore.getState().tabs.map((t) => t.id)).toEqual(["start"]);
  });

  it("drops runs whose tab was closed from the tab strip", () => {
    useWorkspaceStore.getState().closeTab("app:syk-inn");
    dropClosedTabRuns();
    expect(useAppRunStore.getState().runs).toEqual([]);
  });

  it("keeps the tab label in sync with the run status", () => {
    useAppRunStore.getState().setStatus("syk-inn", "running");
    syncRunTabs();
    const tab = useWorkspaceStore.getState().tabs.find((t) => t.id === "app:syk-inn");
    expect(tab?.ariaLabel).toBe(
      copy["tabs.app.aria"]("Sykmelding", "Ola Nordmann", copy["pane.apps.runningHost"]),
    );
    expect(tab?.error).toBe(false);
    useAppRunStore.getState().setStatus("syk-inn", "timeout");
    syncRunTabs();
    expect(
      useWorkspaceStore.getState().tabs.find((t) => t.id === "app:syk-inn")?.error,
    ).toBe(true);
  });

  it("flags the tab as stale when the journal shows another patient", () => {
    seedJournal(kari);
    syncRunTabs();
    expect(
      useWorkspaceStore.getState().tabs.find((t) => t.id === "app:syk-inn")?.ariaLabel,
    ).toContain(copy["s5.status.stale"]("Ola Nordmann"));
  });
});
