import { Outlet, createRootRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { AppDialogs } from "../xp/AppDialogs";
import { AppHeader } from "../xp/AppHeader";
import { AppShell } from "../xp/AppShell";
import { BalloonHost } from "../xp/BalloonHost";
import { JournalGuards } from "../xp/JournalGuards";
import { StatusBar } from "../xp/StatusBar";
import { TaskPane } from "../xp/TaskPane";
import { CurrentUserContext, type CurrentUser } from "../xp/currentUser";
import { UserGate } from "../xp/UserGate";
import { Workspace } from "../xp/Workspace";
import { useAppsStore } from "../xp/appsStore";
import { guardTabClose } from "../xp/journalGuardStore";
import { useLaunchModeStore } from "../xp/launchModeStore";
import { usePatientsStore } from "../xp/patientsStore";
import { currentJournalRoute, openPatientsTab } from "../xp/tabRoutes";
import { useActivePatientSync } from "../xp/useActivePatientSync";
import { useAccessExpiry } from "../xp/useAccessExpiry";
import { useAppSync } from "../xp/useAppSync";
import { useCurrentRoute } from "../xp/useCurrentRoute";
import { logout } from "../xp/logout";
import { useHelsepersonell } from "../xp/useHelsepersonell";
import { useRouteTabSync } from "../xp/useRouteTabSync";
import { useSearchSubmit } from "../xp/useSearchSubmit";
import {
  JOURNAL_TAB_ID,
  useWorkspaceStore,
} from "../xp/workspaceStore";

export const Route = createRootRoute({
  component: RootComponent,
});

function RootComponent() {
  const navigate = useNavigate();
  const route = useCurrentRoute();
  const { state, retry } = useHelsepersonell();
  const currentUser: CurrentUser | null =
    state.status === "ready"
      ? {
          navn: state.helsepersonell.navn,
          hpr: state.helsepersonell.hpr,
          autorisasjon: state.helsepersonell.autorisasjon,
          legekontor: state.legekontor.navn,
          orgnummer: state.legekontor.orgnummer ?? undefined,
          telefon: state.legekontor.tlf ?? undefined,
        }
      : null;
  const openPatients = () => openPatientsTab((target) => void navigate(target));
  const openJournal = () => {
    const target = currentJournalRoute();
    if (!target) return;
    useWorkspaceStore.getState().setCurrent(JOURNAL_TAB_ID);
    void navigate(target);
  };
  const hpr = state.status === "ready" ? state.helsepersonell.hpr : null;
  useEffect(() => usePatientsStore.getState().setOwner(hpr), [hpr]);
  useEffect(() => useLaunchModeStore.getState().setOwner(hpr), [hpr]);
  useEffect(() => {
    if (hpr) void useAppsStore.getState().load();
  }, [hpr]);
  useAppSync();
  useActivePatientSync(hpr !== null);
  useAccessExpiry();
  const submitSearch = useSearchSubmit();
  useRouteTabSync(route, (target) => void navigate(target));

  return (
    <CurrentUserContext.Provider value={currentUser}>
      <AppShell>
        <AppHeader
          user={currentUser}
          onLogout={logout}
          onSearchSubmit={() => void submitSearch()}
        />
        <TaskPane
          userName={currentUser?.navn}
          patientsCurrent={route.kind === "patients"}
          onOpenPatients={openPatients}
          onOpenJournal={openJournal}
          onLogout={logout}
        />
        <Workspace onBeforeCloseTab={guardTabClose}>
          <UserGate state={state} onRetry={retry}>
            <Outlet />
          </UserGate>
        </Workspace>
        <StatusBar onOpenPatients={openPatients} onOpenJournal={openJournal} />
        <BalloonHost />
        <JournalGuards />
        <AppDialogs />
      </AppShell>
    </CurrentUserContext.Provider>
  );
}
