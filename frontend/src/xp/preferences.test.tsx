import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  REDUCE_MOTION_KEY,
  TEXT_SCALE_KEY,
  THEME_KEY,
  readPreferences,
  usePreferencesStore,
} from "./preferencesStore";
import { useTheme } from "./useTheme";
import { useReduceMotion } from "./useReduceMotion";
import { useTextScale } from "./useTextScale";
import { mockMatchMedia } from "./matchMediaMock";

describe("preferences", () => {
  beforeEach(() => {
    usePreferencesStore.setState({
      theme: "luna",
      reduceMotion: false,
      textScale: 100,
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("falls back to defaults when nothing is stored", () => {
    expect(readPreferences()).toEqual({
      theme: "luna",
      reduceMotion: false,
      textScale: 100,
    });
  });

  it("reads stored values", () => {
    localStorage.setItem(THEME_KEY, "klassisk");
    localStorage.setItem(REDUCE_MOTION_KEY, "true");
    localStorage.setItem(TEXT_SCALE_KEY, "150");
    expect(readPreferences()).toEqual({
      theme: "klassisk",
      reduceMotion: true,
      textScale: 150,
    });
  });

  it("ignores invalid stored values", () => {
    localStorage.setItem(THEME_KEY, "neon");
    localStorage.setItem(TEXT_SCALE_KEY, "133");
    expect(readPreferences()).toMatchObject({ theme: "luna", textScale: 100 });
  });

  it("persists the theme and toggles between luna and klassisk", () => {
    const { result } = renderHook(() => useTheme());
    act(() => result.current.toggleTheme());
    expect(result.current.theme).toBe("klassisk");
    expect(localStorage.getItem(THEME_KEY)).toBe("klassisk");
    act(() => result.current.toggleTheme());
    expect(result.current.theme).toBe("luna");
    expect(localStorage.getItem(THEME_KEY)).toBe("luna");
  });

  it("persists the text scale", () => {
    const { result } = renderHook(() => useTextScale());
    act(() => result.current.setTextScale(125));
    expect(result.current.textScale).toBe(125);
    expect(localStorage.getItem(TEXT_SCALE_KEY)).toBe("125");
  });

  it("reduces motion from the stored setting", () => {
    const { result } = renderHook(() => useReduceMotion());
    expect(result.current.effective).toBe(false);
    act(() => result.current.setReduceMotion(true));
    expect(result.current.effective).toBe(true);
    expect(localStorage.getItem(REDUCE_MOTION_KEY)).toBe("true");
  });

  it("reduces motion when the system asks for it", () => {
    const media = mockMatchMedia({ "(prefers-reduced-motion: reduce)": false });
    const { result } = renderHook(() => useReduceMotion());
    expect(result.current.effective).toBe(false);
    act(() => media.set("(prefers-reduced-motion: reduce)", true));
    expect(result.current.systemPrefers).toBe(true);
    expect(result.current.effective).toBe(true);
    expect(result.current.reduceMotion).toBe(false);
  });
});
