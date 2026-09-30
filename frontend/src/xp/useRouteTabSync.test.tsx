import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { copy } from "./copy";
import { useRouteTabSync } from "./useRouteTabSync";
import { useWorkspaceStore } from "./workspaceStore";

const store = () => useWorkspaceStore.getState();

beforeEach(() => store().reset());

describe("useRouteTabSync", () => {
  it("keeps Start selected on the front page", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync("/", navigate));
    expect(store().current).toBe("start");
    expect(store().tabs.map((t) => t.id)).toEqual(["start"]);
    expect(navigate).not.toHaveBeenCalled();
  });

  it("opens and selects the Pasienter tab on a deep link without redirecting", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync("/patients", navigate));
    expect(store().current).toBe("patients");
    expect(store().tabs.map((t) => t.label)).toEqual([
      copy["tabs.start"],
      copy["pane.system.patients"],
    ]);
    expect(store().tabs[1].closable).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it("accepts a trailing slash on the Pasienter route", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync("/patients/", navigate));
    expect(store().current).toBe("patients");
    expect(navigate).not.toHaveBeenCalled();
  });

  it("leaves patient sub-routes unmapped", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync("/patients/abc/konsultasjon/1", navigate));
    expect(store().tabs.map((t) => t.id)).toEqual(["start"]);
    expect(navigate).not.toHaveBeenCalled();
  });

  it("goes to Pasienter from a sub-route when its tab is activated", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync("/patients/abc", navigate));
    act(() => {
      store().openTab({ kind: "patients", label: copy["pane.system.patients"] });
    });
    expect(navigate).toHaveBeenCalledExactlyOnceWith("/patients");
  });

  it("follows route changes without duplicating the tab", () => {
    const navigate = vi.fn();
    const { rerender } = renderHook(
      ({ path }) => useRouteTabSync(path, navigate),
      { initialProps: { path: "/" } },
    );
    rerender({ path: "/patients" });
    expect(store().current).toBe("patients");
    rerender({ path: "/" });
    expect(store().current).toBe("start");
    rerender({ path: "/patients" });
    expect(store().tabs.filter((t) => t.kind === "patients")).toHaveLength(1);
    expect(navigate).not.toHaveBeenCalled();
  });

  it("navigates to the route of a tab activated from the tab strip", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync("/patients", navigate));
    act(() => store().setCurrent("start"));
    expect(navigate).toHaveBeenCalledExactlyOnceWith("/");
  });

  it("navigates to Start when the Pasienter tab is closed", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync("/patients", navigate));
    act(() => store().closeTab("patients"));
    expect(store().current).toBe("start");
    expect(navigate).toHaveBeenCalledExactlyOnceWith("/");
  });

  it("navigates to Pasienter when its tab is reactivated", () => {
    const navigate = vi.fn();
    const { rerender } = renderHook(
      ({ path }) => useRouteTabSync(path, navigate),
      { initialProps: { path: "/patients" } },
    );
    rerender({ path: "/" });
    act(() => store().setCurrent("patients"));
    expect(navigate).toHaveBeenCalledExactlyOnceWith("/patients");
  });

  it("does not navigate when a non-routed tab is selected", () => {
    const navigate = vi.fn();
    renderHook(() => useRouteTabSync("/patients", navigate));
    act(() => {
      store().openTab({ kind: "journal", label: "Journal" });
    });
    expect(navigate).not.toHaveBeenCalled();
  });
});
