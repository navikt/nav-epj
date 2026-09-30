import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { kari, ola, seedApps, seedJournal, seedRun } from "./appFixtures";
import { appTabId } from "./appInfo";
import { useAppRunStore } from "./appRunStore";
import { useAppSync } from "./useAppSync";
import { useWorkspaceStore } from "./workspaceStore";

function openAppTab() {
  useWorkspaceStore
    .getState()
    .openTab({ kind: "app", clientId: "syk-inn", label: "Sykmelding · ON" });
}

describe("useAppSync", () => {
  beforeEach(() => {
    seedApps();
    seedJournal();
    seedRun();
    openAppTab();
  });

  it("drops the run when its tab is closed", () => {
    renderHook(() => useAppSync());
    act(() => useWorkspaceStore.getState().closeTab(appTabId("syk-inn")));
    expect(useAppRunStore.getState().runs).toEqual([]);
  });

  it("marks the tab as failed and labels it when the app times out", () => {
    renderHook(() => useAppSync());
    act(() => useAppRunStore.getState().setStatus("syk-inn", "timeout"));
    const tab = useWorkspaceStore.getState().tabs.find((t) => t.id === appTabId("syk-inn"));
    expect(tab?.error).toBe(true);
    expect(tab?.ariaLabel).toContain("Ola Nordmann");
  });

  it("closes embedded apps of the previous patient when the journal switches", () => {
    renderHook(() => useAppSync());
    act(() => seedJournal(kari));
    expect(useAppRunStore.getState().runs).toEqual([]);
    expect(useWorkspaceStore.getState().tabs.map((t) => t.id)).not.toContain(
      appTabId("syk-inn"),
    );
  });

  it("keeps apps when the journal is closed or shows the same patient", () => {
    renderHook(() => useAppSync());
    act(() => seedJournal(ola));
    expect(useAppRunStore.getState().runs).toHaveLength(1);
  });

  it("stops reacting after unmount", () => {
    const { unmount } = renderHook(() => useAppSync());
    unmount();
    act(() => useWorkspaceStore.getState().closeTab(appTabId("syk-inn")));
    expect(useAppRunStore.getState().runs).toHaveLength(1);
  });
});
