import { useCallback, useEffect, useState } from "react";
import {
  fetchCapabilityStatement,
  fetchSession,
  fetchSmartConfiguration,
} from "./api";
import type {
  CapabilityStatement,
  Session,
  SmartConfiguration,
} from "../utils/mapping/epj";

export type Loadable<T> =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; data: T };

function useLoadable<T>(load: () => Promise<T>, attempt: number) {
  const [state, setState] = useState<Loadable<T>>({ status: "loading" });
  useEffect(() => {
    let current = true;
    setState({ status: "loading" });
    load().then(
      (data) => {
        if (current) setState({ status: "ready", data });
      },
      () => {
        if (current) setState({ status: "error" });
      },
    );
    return () => {
      current = false;
    };
  }, [load, attempt]);
  return state;
}

export function useSystemInfo() {
  const [attempt, setAttempt] = useState(0);
  const session: Loadable<Session> = useLoadable(fetchSession, attempt);
  const smart: Loadable<SmartConfiguration> = useLoadable(
    fetchSmartConfiguration,
    attempt,
  );
  const fhir: Loadable<CapabilityStatement> = useLoadable(
    fetchCapabilityStatement,
    attempt,
  );
  const retry = useCallback(() => setAttempt((n) => n + 1), []);
  return { session, smart, fhir, retry };
}
