import { vi } from "vitest";

type Listener = () => void;

export function mockMatchMedia(initial: Record<string, boolean> = {}) {
  const state = new Map(Object.entries(initial));
  const listeners = new Map<string, Set<Listener>>();

  vi.stubGlobal(
    "matchMedia",
    (query: string) => ({
      get matches() {
        return state.get(query) ?? false;
      },
      media: query,
      addEventListener: (_: string, l: Listener) => {
        const set = listeners.get(query) ?? new Set();
        set.add(l);
        listeners.set(query, set);
      },
      removeEventListener: (_: string, l: Listener) => {
        listeners.get(query)?.delete(l);
      },
    }),
  );

  return {
    set(query: string, value: boolean) {
      state.set(query, value);
      listeners.get(query)?.forEach((l) => l());
    },
  };
}
