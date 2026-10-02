import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { kari, ola } from "./appFixtures";
import { useAppDialogStore } from "./appDialogStore";
import { APP_ACCESS_MS, useAppRunStore } from "./appRunStore";
import { expireTabApps } from "./launchApp";
import { useAccessExpiry } from "./useAccessExpiry";

const START = new Date(2026, 8, 30, 9, 14);

function addTabApp(id: string, startedAt: Date, patient = ola) {
  useAppRunStore.getState().addTabApp({
    id,
    clientId: "syk-inn",
    navn: "Sykmelding",
    patient,
    startedAt,
  });
}

const ids = () => useAppRunStore.getState().tabApps.map((a) => a.id);

describe("access expiry of browser-tab apps", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(START);
  });
  afterEach(() => vi.useRealTimers());

  it("removes a tab app one hour after it started", () => {
    addTabApp("smart-syk-inn-1", START);
    renderHook(() => useAccessExpiry());
    act(() => vi.advanceTimersByTime(APP_ACCESS_MS - 1000));
    expect(ids()).toEqual(["smart-syk-inn-1"]);
    act(() => vi.advanceTimersByTime(1100));
    expect(ids()).toEqual([]);
  });

  it("expires each entry at its own time", () => {
    addTabApp("smart-syk-inn-1", START, kari);
    addTabApp("smart-syk-inn-2", new Date(START.getTime() + 20 * 60 * 1000));
    renderHook(() => useAccessExpiry());
    act(() => vi.advanceTimersByTime(APP_ACCESS_MS + 100));
    expect(ids()).toEqual(["smart-syk-inn-2"]);
    act(() => vi.advanceTimersByTime(20 * 60 * 1000));
    expect(ids()).toEqual([]);
  });

  it("schedules entries added after mount", () => {
    renderHook(() => useAccessExpiry());
    act(() => addTabApp("smart-syk-inn-1", START));
    act(() => vi.advanceTimersByTime(APP_ACCESS_MS + 100));
    expect(ids()).toEqual([]);
  });

  it("removes an entry that is already expired when the app mounts", () => {
    addTabApp("smart-syk-inn-1", new Date(START.getTime() - 2 * APP_ACCESS_MS));
    renderHook(() => useAccessExpiry());
    act(() => vi.advanceTimersByTime(100));
    expect(ids()).toEqual([]);
  });

  it("closes the tab app dialog of an expired entry", () => {
    addTabApp("smart-syk-inn-1", START);
    useAppDialogStore.getState().show({ kind: "tabApp", tabId: "smart-syk-inn-1" });
    expireTabApps(new Date(START.getTime() + APP_ACCESS_MS));
    expect(useAppDialogStore.getState().dialog).toBeNull();
  });

  it("leaves other dialogs open", () => {
    addTabApp("smart-syk-inn-1", START);
    useAppDialogStore.getState().show({ kind: "tabApp", tabId: "other" });
    expireTabApps(new Date(START.getTime() + APP_ACCESS_MS));
    expect(useAppDialogStore.getState().dialog).toEqual({ kind: "tabApp", tabId: "other" });
  });
});
