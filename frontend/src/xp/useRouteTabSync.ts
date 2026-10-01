import { useEffect, useRef } from "react";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import { journalRoute, routeForTab, type TabRoute } from "./tabRoutes";
import type { CurrentRoute } from "./useCurrentRoute";
import {
  JOURNAL_TAB_ID,
  START_TAB_ID,
  useWorkspaceStore,
} from "./workspaceStore";

function subTabOf(route: CurrentRoute) {
  if (route.kind !== "journal") return null;
  return route.konsultasjonId ? "konsultasjon" : (route.tab ?? "konsultasjon");
}

export function useRouteTabSync(
  route: CurrentRoute,
  navigate: (target: TabRoute) => void,
) {
  const routeRef = useRef(route);
  const applyingRoute = useRef(false);
  const navigateRef = useRef(navigate);
  const patientId = route.kind === "journal" ? route.patientId : undefined;
  const konsultasjonId =
    route.kind === "journal" ? route.konsultasjonId : undefined;
  const subTab = subTabOf(route);

  useEffect(() => {
    routeRef.current = route;
    navigateRef.current = navigate;
  });

  useEffect(() => {
    const { openTab, setCurrent } = useWorkspaceStore.getState();
    if (route.kind === "start") {
      setCurrent(START_TAB_ID);
    } else if (route.kind === "patients") {
      openTab({ kind: "patients", label: copy["pane.system.patients"] });
    } else if (patientId) {
      const exists = useWorkspaceStore
        .getState()
        .tabs.some((t) => t.id === JOURNAL_TAB_ID);
      if (exists) setCurrent(JOURNAL_TAB_ID);
      else openTab({ kind: "journal", label: copy["s4.tabs.label"] });
      applyingRoute.current = true;
      void useJournalStore.getState().open(patientId, konsultasjonId);
      applyingRoute.current = false;
    }
  }, [route.kind, patientId, konsultasjonId]);

  useEffect(() => {
    if (!subTab) return;
    applyingRoute.current = true;
    useJournalStore.getState().setSubTab(subTab);
    applyingRoute.current = false;
  }, [patientId, konsultasjonId, subTab]);

  useEffect(
    () =>
      useJournalStore.subscribe((state, previous) => {
        if (applyingRoute.current || state.subTab === previous.subTab) return;
        const current = routeRef.current;
        if (current.kind !== "journal" || current.patientId !== state.patientId) {
          return;
        }
        if (state.subTab === subTabOf(current)) return;
        navigateRef.current({
          ...journalRoute(
            current.patientId,
            state.selectedKonsultasjonId,
            state.subTab,
          ),
          replace: true,
        });
      }),
    [],
  );

  useEffect(
    () =>
      useWorkspaceStore.subscribe((state, previous) => {
        const hadJournal = previous.tabs.some((t) => t.id === JOURNAL_TAB_ID);
        if (hadJournal && !state.tabs.some((t) => t.id === JOURNAL_TAB_ID)) {
          useJournalStore.getState().clear();
        }
        if (state.current === previous.current) return;
        const active = state.tabs.find((t) => t.id === state.current);
        if (!active || routeRef.current.kind === active.kind) return;
        const target = routeForTab(active);
        if (target) navigateRef.current(target);
      }),
    [],
  );
}
