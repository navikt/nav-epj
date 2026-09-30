import { Outlet, createRootRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { AppHeader } from "../xp/AppHeader";
import { AppShell } from "../xp/AppShell";
import { BalloonHost } from "../xp/BalloonHost";
import { JournalGuards } from "../xp/JournalGuards";
import { StatusBar } from "../xp/StatusBar";
import { TaskPane } from "../xp/TaskPane";
import { CurrentUserContext, type CurrentUser } from "../xp/currentUser";
import { UserGate } from "../xp/UserGate";
import { Workspace } from "../xp/Workspace";
import { guardTabClose } from "../xp/journalGuardStore";
import { usePatientsStore } from "../xp/patientsStore";
import { currentJournalRoute } from "../xp/tabRoutes";
import { useCurrentRoute } from "../xp/useCurrentRoute";
import { logout } from "../xp/logout";
import { useHelsepersonell } from "../xp/useHelsepersonell";
import { useRouteTabSync } from "../xp/useRouteTabSync";
import { useSearchSubmit } from "../xp/useSearchSubmit";

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
          autorisasjon: state.helsepersonell.autorisasjon,
          legekontor: state.legekontor.navn,
          orgnummer: state.legekontor.orgnummer ?? undefined,
        }
      : null;
  const openPatients = () => void navigate({ to: "/patients" });
  const openJournal = () => {
    const target = currentJournalRoute();
    if (target) void navigate(target);
  };
  const hpr = state.status === "ready" ? state.helsepersonell.hpr : null;
  useEffect(() => usePatientsStore.getState().setOwner(hpr), [hpr]);
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
      </AppShell>
    </CurrentUserContext.Provider>
  );
}
