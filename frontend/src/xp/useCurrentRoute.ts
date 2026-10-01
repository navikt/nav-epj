import { useRouterState } from "@tanstack/react-router";
import { useMemo } from "react";

export type CurrentRoute =
  | { kind: "start" }
  | { kind: "patients" }
  | { kind: "journal"; patientId: string; konsultasjonId?: string }
  | { kind: "other" };

const JOURNAL_ROUTE_ID = "/patients/$patientId";

type RouteParams = { patientId?: string; konsultasjonId?: string };

export function useCurrentRoute(): CurrentRoute {
  const routeId = useRouterState({ select: (s) => s.matches.at(-1)?.routeId });
  const isJournal = useRouterState({
    select: (s) => s.matches.some((m) => m.routeId === JOURNAL_ROUTE_ID),
  });
  const patientId = useRouterState({
    select: (s) => (s.matches.at(-1)?.params as RouteParams | undefined)?.patientId,
  });
  const konsultasjonId = useRouterState({
    select: (s) =>
      (s.matches.at(-1)?.params as RouteParams | undefined)?.konsultasjonId,
  });

  return useMemo<CurrentRoute>(() => {
    if (isJournal && patientId) {
      return { kind: "journal", patientId, konsultasjonId };
    }
    if (routeId === "/") return { kind: "start" };
    if (routeId === "/patients/") return { kind: "patients" };
    return { kind: "other" };
  }, [isJournal, patientId, konsultasjonId, routeId]);
}
