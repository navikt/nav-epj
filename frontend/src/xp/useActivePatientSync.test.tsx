import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useActivePatientStore } from "./activePatientStore";
import { useActivePatientSync } from "./useActivePatientSync";

function stubActive(patientId: string) {
  const fn = vi.fn(async () => ({
    ok: true,
    status: 200,
    json: async () => ({ patientId, expiresAt: "2026-09-30T17:14:00Z" }),
  }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

function setVisibility(state: DocumentVisibilityState) {
  Object.defineProperty(document, "visibilityState", {
    value: state,
    configurable: true,
  });
}

afterEach(() => {
  setVisibility("visible");
  vi.unstubAllGlobals();
});

describe("useActivePatientSync", () => {
  it("checks the active patient when the window regains focus", async () => {
    const fetch = stubActive("p2");
    renderHook(() => useActivePatientSync(true));
    act(() => {
      window.dispatchEvent(new Event("focus"));
    });
    await vi.waitFor(() =>
      expect(useActivePatientStore.getState().activeId).toBe("p2"),
    );
    expect(fetch).toHaveBeenCalledWith("/api/active-patient", undefined);
  });

  it("checks when the tab becomes visible but not when it becomes hidden", async () => {
    const fetch = stubActive("p2");
    renderHook(() => useActivePatientSync(true));
    setVisibility("hidden");
    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });
    expect(fetch).not.toHaveBeenCalled();
    setVisibility("visible");
    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledOnce());
  });

  it("does nothing while disabled and stops listening on unmount", () => {
    const fetch = stubActive("p2");
    const disabled = renderHook(() => useActivePatientSync(false));
    act(() => {
      window.dispatchEvent(new Event("focus"));
    });
    disabled.unmount();
    const enabled = renderHook(() => useActivePatientSync(true));
    enabled.unmount();
    act(() => {
      window.dispatchEvent(new Event("focus"));
    });
    expect(fetch).not.toHaveBeenCalled();
  });
});
