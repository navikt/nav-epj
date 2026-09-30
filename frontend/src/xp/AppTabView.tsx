import { useEffect, useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { format, parseISO } from "date-fns";
import { AppFrame } from "./AppFrame";
import { AppToolbar } from "./AppToolbar";
import { DevPanel } from "./DevPanel";
import { PatientContext } from "./PatientContext";
import { appIconName } from "./appInfo";
import { accessExpiry, isStaleFor, useAppRunStore } from "./appRunStore";
import { useAppsStore } from "./appsStore";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import { closeApp, reloadApp } from "./launchApp";
import { fullName } from "./patientInfo";
import { useShell } from "./shellContext";
import { currentJournalRoute } from "./tabRoutes";
import { useStartApp } from "./useStartApp";
import { JOURNAL_TAB_ID, useWorkspaceStore } from "./workspaceStore";

type Props = { clientId: string };

export function AppTabView({ clientId }: Props) {
  const run = useAppRunStore((s) => s.runs.find((r) => r.clientId === clientId));
  const app = useAppsStore((s) => s.apps.find((a) => a.clientId === clientId));
  const journalPatient = useJournalStore((s) => s.patient);
  const journalPatientId = useJournalStore((s) => s.patientId);
  const { announce } = useShell();
  const { popOut } = useStartApp();
  const navigate = useNavigate();
  const [devOpen, setDevOpen] = useState(false);
  const status = run?.status;
  const navn = run?.navn;

  useEffect(() => {
    if (status === "running" && navn) announce(copy["live.appRunning"](navn));
  }, [status, navn, announce]);

  if (!run) return null;
  const stale = isStaleFor(run.patient.id, journalPatientId);
  const patientName = fullName(run.patient);
  const started = format(run.startedAt, "HH:mm");
  const canNavigate = run.status === "running" && !stale;
  const canReload = run.status !== "starting" && run.status !== "session" && !stale;

  function openJournal() {
    const target = currentJournalRoute();
    useWorkspaceStore.getState().setCurrent(JOURNAL_TAB_ID);
    if (target) void navigate(target);
  }

  let footer: string = copy["s5.status.starting"];
  if (stale) footer = copy["s5.status.stale"](patientName);
  else if (run.status === "running") {
    footer = copy["s5.status.running"](
      started,
      format(accessExpiry(run.startedAt), "HH:mm"),
    );
  } else if (run.status === "timeout") footer = copy["s5.status.timeout"];
  else if (run.status === "error") footer = copy["s5.status.error"];
  else if (run.status === "session") footer = copy["s5.status.session"];

  return (
    <>
      <PatientContext pasient={run.patient} compact stale={stale} />
      <AppToolbar
        clientId={clientId}
        app={run.navn}
        icon={appIconName(app?.ikon ?? "")}
        title={copy["s5.title"](
          run.navn,
          patientName,
          format(parseISO(run.konsultasjon.startetTidspunkt), "dd.MM HH:mm"),
        )}
        canNavigate={canNavigate}
        canReload={canReload}
        devOpen={devOpen}
        onBack={() => {
          window.history.back();
          announce(copy["live.back"](run.navn));
        }}
        onForward={() => {
          window.history.forward();
          announce(copy["live.forward"](run.navn));
        }}
        onReload={() => void reloadApp(clientId)}
        onToggleDev={() => setDevOpen(!devOpen)}
        onPopOut={() => void popOut(clientId)}
        onClose={() => closeApp(clientId)}
      />
      <AppFrame
        run={run}
        stale={stale}
        journalPatientName={journalPatient ? fullName(journalPatient) : null}
        onRestart={() => void reloadApp(clientId)}
        onPopOut={() => void popOut(clientId)}
        onClose={() => closeApp(clientId)}
        onOpenJournal={openJournal}
      />
      <div className="xp-frame-foot">
        <span className="xp-status-text">{footer}</span>
      </div>
      <DevPanel run={run} hidden={!devOpen} onClose={() => setDevOpen(false)} />
    </>
  );
}
