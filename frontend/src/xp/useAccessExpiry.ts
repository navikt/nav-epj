import { useEffect } from "react";
import { accessExpiry, useAppRunStore } from "./appRunStore";
import { expireTabApps } from "./launchApp";

export function useAccessExpiry() {
  const tabApps = useAppRunStore((s) => s.tabApps);
  useEffect(() => {
    if (tabApps.length === 0) return;
    const next = Math.min(
      ...tabApps.map((a) => accessExpiry(a.startedAt).getTime()),
    );
    const timer = setTimeout(
      () => expireTabApps(),
      Math.max(next - Date.now(), 0) + 50,
    );
    return () => clearTimeout(timer);
  }, [tabApps]);
}
