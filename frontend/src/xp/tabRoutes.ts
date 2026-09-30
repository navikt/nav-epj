import { useJournalStore } from "./journalStore";
import type { Tab } from "./workspaceStore";

export type TabRoute =
  | { to: "/" }
  | { to: "/patients" }
  | { to: "/patients/$patientId"; params: { patientId: string } }
  | {
      to: "/patients/$patientId/konsultasjon/$konsultasjonId";
      params: { patientId: string; konsultasjonId: string };
    };

export function journalRoute(
  patientId: string,
  konsultasjonId?: string | null,
): TabRoute {
  return konsultasjonId
    ? {
        to: "/patients/$patientId/konsultasjon/$konsultasjonId",
        params: { patientId, konsultasjonId },
      }
    : { to: "/patients/$patientId", params: { patientId } };
}

export function currentJournalRoute(): TabRoute | null {
  const { patientId, selectedKonsultasjonId } = useJournalStore.getState();
  return patientId ? journalRoute(patientId, selectedKonsultasjonId) : null;
}

function assertNever(value: never): never {
  throw new Error(`Unhandled tab kind: ${String(value)}`);
}

export function routeForTab(tab: Tab): TabRoute | null {
  switch (tab.kind) {
    case "start":
      return { to: "/" };
    case "patients":
      return { to: "/patients" };
    case "journal":
      return currentJournalRoute();
    case "app":
    case "kontrollpanel":
    case "sysinfo":
    case "hjelp":
    case "hendelseslogg":
      return null;
    default:
      return assertNever(tab);
  }
}
