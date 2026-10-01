import { useSyncExternalStore } from "react";

const MINUTE_MS = 60_000;

const listeners = new Set<() => void>();
let timer: ReturnType<typeof setTimeout> | undefined;
let snapshot = startOfMinute();

function startOfMinute() {
  return new Date(Math.floor(Date.now() / MINUTE_MS) * MINUTE_MS);
}

function refresh() {
  const next = startOfMinute();
  if (next.getTime() !== snapshot.getTime()) snapshot = next;
}

function schedule() {
  timer = setTimeout(() => {
    refresh();
    listeners.forEach((listener) => listener());
    schedule();
  }, MINUTE_MS - (Date.now() % MINUTE_MS));
}

function subscribe(listener: () => void) {
  if (listeners.size === 0) {
    refresh();
    schedule();
  }
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) clearTimeout(timer);
  };
}

function getSnapshot() {
  if (listeners.size === 0) refresh();
  return snapshot;
}

export function useNow() {
  return useSyncExternalStore(subscribe, getSnapshot);
}
