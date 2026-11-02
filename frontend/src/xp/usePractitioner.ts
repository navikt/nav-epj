import { useCallback, useEffect, useState } from "react";
import { expireSession } from "./sessionExpiry";
import { chooseSessionOrg, fetchSessionOrg } from "./api";
import { onSessionOrgConflict } from "./sessionOrgConflict";
import {
  PractitionerSchema,
  SessionOrgSchema,
  type Practitioner,
  type SessionOrg,
} from "../utils/mapping/epj";

export type PractitionerState =
  | { status: "loading" }
  | { status: "error" }
  | {
      status: "ready";
      practitioner: Practitioner;
      sessionOrg: SessionOrg;
    };

async function fetchJson(url: string, signal: AbortSignal) {
  const response = await fetch(url, { signal });
  if (response.status === 401) expireSession(`GET ${url}`);
  if (!response.ok) throw new Error(String(response.status));
  return response.json();
}

export function usePractitioner() {
  const [state, setState] = useState<PractitionerState>({
    status: "loading",
  });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    (async () => {
      try {
        const practitioner = PractitionerSchema.parse(
          await fetchJson("/api/practitioners/me", signal),
        );
        const sessionOrg = SessionOrgSchema.parse(
          await fetchJson("/api/session-org", signal),
        );
        setState({ status: "ready", practitioner, sessionOrg });
      } catch {
        if (!signal.aborted) setState({ status: "error" });
      }
    })();
    return () => controller.abort();
  }, [attempt]);

  useEffect(
    () =>
      onSessionOrgConflict(() => {
        fetchSessionOrg()
          .then((sessionOrg) =>
            setState((current) =>
              current.status === "ready" ? { ...current, sessionOrg } : current,
            ),
          )
          .catch(() => undefined);
      }),
    [],
  );

  const retry = useCallback(() => {
    setState({ status: "loading" });
    setAttempt((n) => n + 1);
  }, []);

  const chooseOrg = useCallback(async (orgNumber: string) => {
    const sessionOrg = await chooseSessionOrg(orgNumber);
    setState((current) =>
      current.status === "ready" ? { ...current, sessionOrg } : current,
    );
  }, []);

  return { state, retry, chooseOrg };
}
