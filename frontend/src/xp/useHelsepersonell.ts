import { useCallback, useEffect, useState } from "react";
import { expireSession } from "./sessionExpiry";
import {
  HelsepersonellSchema,
  LegekontorSchema,
  type Helsepersonell,
  type Legekontor,
} from "../utils/mapping/epj";

export type HelsepersonellState =
  | { status: "loading" }
  | { status: "error" }
  | {
      status: "ready";
      helsepersonell: Helsepersonell;
      legekontor: Legekontor;
    };

async function fetchJson(url: string, signal: AbortSignal) {
  const response = await fetch(url, { signal });
  if (response.status === 401) expireSession(`GET ${url}`);
  if (!response.ok) throw new Error(String(response.status));
  return response.json();
}

export function useHelsepersonell() {
  const [state, setState] = useState<HelsepersonellState>({
    status: "loading",
  });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    setState({ status: "loading" });
    (async () => {
      try {
        const helsepersonell = HelsepersonellSchema.parse(
          await fetchJson("/api/helsepersonell/me", signal),
        );
        const legekontor = LegekontorSchema.parse(
          await fetchJson(
            `/api/legekontor/${encodeURIComponent(helsepersonell.legekontorId)}`,
            signal,
          ),
        );
        setState({ status: "ready", helsepersonell, legekontor });
      } catch {
        if (!signal.aborted) setState({ status: "error" });
      }
    })();
    return () => controller.abort();
  }, [attempt]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);

  return { state, retry };
}
