import { useRouterState } from "@tanstack/react-router";
import type { JournalSubTab } from "./journalStore";

export type CurrentRoute =
  | { kind: "start" }
  | { kind: "patients" }
  | {
      kind: "journal";
      patientId: string;
      konsultasjonId?: string;
      tab?: JournalSubTab;
    }
  | { kind: "other" };

type RouteParams = { patientId?: string; konsultasjonId?: string };

export function useCurrentRoute(): CurrentRoute {
  return useRouterState({
    structuralSharing: true,
    select: (s): CurrentRoute => {
      const match = s.matches.at(-1);
      const { patientId, konsultasjonId } = (match?.params as RouteParams) ?? {};
      if (
        patientId &&
        s.matches.some((m) => m.routeId === "/patients/$patientId")
      ) {
        const { tab } = match?.search as { tab?: JournalSubTab };
        return { kind: "journal", patientId, konsultasjonId, tab };
      }
      if (match?.routeId === "/") return { kind: "start" };
      if (match?.routeId === "/patients/") return { kind: "patients" };
      return { kind: "other" };
    },
  });
}
