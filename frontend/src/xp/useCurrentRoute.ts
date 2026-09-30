import { useRouterState } from "@tanstack/react-router";
import { useMemo } from "react";

export type CurrentRoute =
  | { kind: "start" }
  | { kind: "patients" }
  | { kind: "journal"; patientId: string; konsultasjonId?: string }
  | { kind: "other" };

const JOURNAL_ROUTE_ID = "/patients/$patientId";

export function useCurrentRoute(): CurrentRoute {
  const matches = useRouterState({ select: (s) => s.matches });
  const last = matches.at(-1);
  const isJournal = matches.some((m) => m.routeId === JOURNAL_ROUTE_ID);
  const params = (last?.params ?? {}) as {
    patientId?: string;
    konsultasjonId?: string;
  };
  const routeId = last?.routeId;
  const { patientId, konsultasjonId } = params;

  return useMemo<CurrentRoute>(() => {
    if (isJournal && patientId) {
      return { kind: "journal", patientId, konsultasjonId };
    }
    if (routeId === "/") return { kind: "start" };
    if (routeId === "/patients/") return { kind: "patients" };
    return { kind: "other" };
  }, [isJournal, patientId, konsultasjonId, routeId]);
}
