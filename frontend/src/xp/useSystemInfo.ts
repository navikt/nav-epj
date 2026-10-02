import { useCallback, useEffect, useState } from "react";
import {
  fetchCapabilityStatement,
  fetchSession,
  fetchSmartConfiguration,
} from "./api";

export type Loadable<T> =
  { status: "loading" } | { status: "error" } | { status: "ready"; data: T };

function useLoadable<T>(load: () => Promise<T>) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<Loadable<T>>({ status: "loading" });
  useEffect(() => {
    let current = true;
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
  const retry = useCallback(() => {
    setState({ status: "loading" });
    setAttempt((n) => n + 1);
  }, []);
  return { state, retry };
}

export function useSystemInfo() {
  const session = useLoadable(fetchSession);
  const smart = useLoadable(fetchSmartConfiguration);
  const fhir = useLoadable(fetchCapabilityStatement);
  return { session, smart, fhir };
}
