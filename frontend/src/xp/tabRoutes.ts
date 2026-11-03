import { useJournalStore, type JournalSubTab } from "./journalStore";
import { PATIENTS_TAB_ID, useWorkspaceStore } from "./workspaceStore";
import type { Tab } from "./workspaceStore";

export type TabRoute = (
  | { to: "/" }
  | { to: "/patients" }
  | {
      to: "/patients/$patientId";
      params: { patientId: string };
      search?: { tab: JournalSubTab };
    }
  | {
      to: "/patients/$patientId/consultations/$consultationId";
      params: { patientId: string; consultationId: string };
    }
) & { replace?: boolean };

export function openPatientsTab(navigate: (target: TabRoute) => void) {
  const workspace = useWorkspaceStore.getState();
  if (workspace.tabs.some((t) => t.id === PATIENTS_TAB_ID)) {
    workspace.setCurrent(PATIENTS_TAB_ID);
  }
  navigate({ to: "/patients" });
}

export function journalRoute(
  patientId: string,
  consultationId?: string | null,
  tab: JournalSubTab = "consultation",
): TabRoute {
  if (tab !== "consultation") {
    return {
      to: "/patients/$patientId",
      params: { patientId },
      search: { tab },
    };
  }
  return consultationId
    ? {
        to: "/patients/$patientId/consultations/$consultationId",
        params: { patientId, consultationId },
      }
    : { to: "/patients/$patientId", params: { patientId } };
}

export function currentJournalRoute(): TabRoute | null {
  const { patientId, selectedConsultationId, subTab } =
    useJournalStore.getState();
  return patientId
    ? journalRoute(patientId, selectedConsultationId, subTab)
    : null;
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
