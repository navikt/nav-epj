import { Outlet, createRootRoute, useNavigate, useRouterState } from "@tanstack/react-router";
import { AppHeader } from "../xp/AppHeader";
import { AppShell } from "../xp/AppShell";
import { StatusBar } from "../xp/StatusBar";
import { TaskPane } from "../xp/TaskPane";
import { UserGate } from "../xp/UserGate";
import { Workspace } from "../xp/Workspace";
import { logout } from "../xp/logout";
import { useHelsepersonell } from "../xp/useHelsepersonell";
import { useRouteTabSync } from "../xp/useRouteTabSync";

export const Route = createRootRoute({
  component: RootComponent,
});

function RootComponent() {
  const navigate = useNavigate();
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const { state, retry } = useHelsepersonell();
  const user =
    state.status === "ready"
      ? {
          navn: state.helsepersonell.navn,
          autorisasjon: state.helsepersonell.autorisasjon,
          legekontor: state.legekontor.navn,
        }
      : null;
  const openPatients = () => void navigate({ to: "/patients" });
  useRouteTabSync(pathname, (to) => void navigate({ to }));

  return (
    <AppShell>
      <AppHeader
        user={user}
        onLogout={logout}
        onSearchSubmit={openPatients}
      />
      <TaskPane
        userName={user?.navn}
        patientsCurrent={pathname.startsWith("/patients")}
        onOpenPatients={openPatients}
        onLogout={logout}
      />
      <Workspace>
        <UserGate state={state} onRetry={retry}>
          <Outlet />
        </UserGate>
      </Workspace>
      <StatusBar onOpenPatients={openPatients} />
    </AppShell>
  );
}
