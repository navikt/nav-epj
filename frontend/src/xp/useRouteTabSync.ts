import { useEffect, useRef } from "react";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import {
  JOURNAL_TAB_ID,
  START_TAB_ID,
  useWorkspaceStore,
} from "./workspaceStore";

export type TabRoute =
  | { to: "/" }
  | { to: "/patients" }
  | { to: "/patients/$patientId"; params: { patientId: string } };

const JOURNAL_PATH = /^\/patients\/([^/]+)(?:\/konsultasjon\/([^/]+)(?:\/.*)?)?$/;

function normalize(pathname: string) {
  return pathname.replace(/\/+$/, "") || "/";
}

export function parseJournalPath(pathname: string) {
  const match = JOURNAL_PATH.exec(normalize(pathname));
  if (!match) return null;
  return {
    patientId: decodeURIComponent(match[1]),
    konsultasjonId: match[2] ? decodeURIComponent(match[2]) : undefined,
  };
}

export function useRouteTabSync(
  pathname: string,
  navigate: (target: TabRoute) => void,
) {
  const pathnameRef = useRef(pathname);
  const navigateRef = useRef(navigate);

  useEffect(() => {
    pathnameRef.current = pathname;
    navigateRef.current = navigate;
  });

  useEffect(() => {
    const { openTab, setCurrent } = useWorkspaceStore.getState();
    const path = normalize(pathname);
    const journal = parseJournalPath(path);
    if (path === "/") {
      setCurrent(START_TAB_ID);
    } else if (path === "/patients") {
      openTab({ kind: "patients", label: copy["pane.system.patients"] });
    } else if (journal) {
      const exists = useWorkspaceStore
        .getState()
        .tabs.some((t) => t.id === JOURNAL_TAB_ID);
      if (exists) setCurrent(JOURNAL_TAB_ID);
      else openTab({ kind: "journal", label: copy["s4.tabs.label"] });
      void useJournalStore
        .getState()
        .open(journal.patientId, journal.konsultasjonId);
    }
  }, [pathname]);

  useEffect(
    () =>
      useWorkspaceStore.subscribe((state, previous) => {
        const hadJournal = previous.tabs.some((t) => t.id === JOURNAL_TAB_ID);
        if (hadJournal && !state.tabs.some((t) => t.id === JOURNAL_TAB_ID)) {
          useJournalStore.getState().clear();
        }
        if (state.current === previous.current) return;
        const path = normalize(pathnameRef.current);
        if (state.current === START_TAB_ID && path !== "/") {
          navigateRef.current({ to: "/" });
        } else if (state.current === "patients" && path !== "/patients") {
          navigateRef.current({ to: "/patients" });
        } else if (state.current === JOURNAL_TAB_ID) {
          const { patientId } = useJournalStore.getState();
          if (patientId && !parseJournalPath(path)) {
            navigateRef.current({
              to: "/patients/$patientId",
              params: { patientId },
            });
          }
        }
      }),
    [],
  );
}
