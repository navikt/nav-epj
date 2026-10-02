import { describe, expect, it, vi } from "vitest";
import { useLaunchModeStore } from "./launchModeStore";

describe("launchModeStore", () => {
  it("keeps remembered choices per clinician", () => {
    const { setOwner, remember } = useLaunchModeStore.getState();
    setOwner("111");
    remember("validator", "tab");
    setOwner("222");
    expect(useLaunchModeStore.getState().choices).toEqual({});
    remember("validator", "iframe");
    setOwner("111");
    expect(useLaunchModeStore.getState().choices).toEqual({ validator: "tab" });
    expect(localStorage.getItem("nav-epj:launchMode:222")).toBe(
      JSON.stringify({ validator: "iframe" }),
    );
  });

  it("does not persist without a signed-in clinician", () => {
    useLaunchModeStore.getState().remember("validator", "tab");
    expect(localStorage.length).toBe(0);
  });

  it("ignores malformed stored values", () => {
    localStorage.setItem(
      "nav-epj:launchMode:111",
      JSON.stringify({ a: "iframe", b: "popup", c: 3 }),
    );
    useLaunchModeStore.getState().setOwner("111");
    expect(useLaunchModeStore.getState().choices).toEqual({ a: "iframe" });
  });

  it("survives corrupt JSON and storage failures", () => {
    localStorage.setItem("nav-epj:launchMode:111", "{");
    useLaunchModeStore.getState().setOwner("111");
    expect(useLaunchModeStore.getState().choices).toEqual({});
    const spy = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("quota");
    });
    useLaunchModeStore.getState().remember("validator", "tab");
    expect(useLaunchModeStore.getState().choices).toEqual({ validator: "tab" });
    spy.mockRestore();
  });

  it("removes the legacy unscoped key", () => {
    localStorage.setItem("nav-epj:launchMode", "{}");
    useLaunchModeStore.getState().setOwner("111");
    expect(localStorage.getItem("nav-epj:launchMode")).toBeNull();
  });
});
