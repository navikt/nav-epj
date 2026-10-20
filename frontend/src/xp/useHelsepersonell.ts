import { useCallback, useEffect, useState } from "react";
import { expireSession } from "./sessionExpiry";
import { chooseSessionOrg } from "./api";
import {
  HelsepersonellSchema,
  SessionOrgSchema,
  type Helsepersonell,
  type SessionOrg,
} from "../utils/mapping/epj";

export type HelsepersonellState =
  | { status: "loading" }
  | { status: "error" }
  | {
      status: "ready";
      helsepersonell: Helsepersonell;
      sessionOrg: SessionOrg;
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
    (async () => {
      try {
        const helsepersonell = HelsepersonellSchema.parse(
          await fetchJson("/api/helsepersonell/me", signal),
        );
        const sessionOrg = SessionOrgSchema.parse(
          await fetchJson("/api/session-org", signal),
        );
        setState({ status: "ready", helsepersonell, sessionOrg });
      } catch {
        if (!signal.aborted) setState({ status: "error" });
      }
    })();
    return () => controller.abort();
  }, [attempt]);

  const retry = useCallback(() => {
    setState({ status: "loading" });
    setAttempt((n) => n + 1);
  }, []);

  const chooseOrg = useCallback(async (orgnummer: string) => {
    const sessionOrg = await chooseSessionOrg(orgnummer);
    setState((current) =>
      current.status === "ready" ? { ...current, sessionOrg } : current,
    );
  }, []);

  return { state, retry, chooseOrg };
}
