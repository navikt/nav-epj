import { useEffect, useState } from "react";
import { format, parseISO } from "date-fns";
import { AppFrame } from "./AppFrame";
import { AppToolbar } from "./AppToolbar";
import { DevPanel } from "./DevPanel";
import { PatientContext } from "./PatientContext";
import { appIconName, appTabId } from "./appInfo";
import { useActivePatientStore } from "./activePatientStore";
import { accessExpiry, isStaleFor, useAppRunStore } from "./appRunStore";
import { useAppsStore } from "./appsStore";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import { closeApp, reloadApp } from "./launchApp";
import { fullName } from "./patientInfo";
import { useShell } from "./shellContext";
import { useOpenJournal } from "./useOpenJournal";
import { usePatientLabel } from "./usePatientName";
import { useStartApp } from "./useStartApp";
import { useWorkspaceStore } from "./workspaceStore";

type Props = { clientId: string };

export function AppTabView({ clientId }: Props) {
  const run = useAppRunStore((s) => s.runs.find((r) => r.clientId === clientId));
  const app = useAppsStore((s) => s.apps.find((a) => a.clientId === clientId));
  const journalPatientId = useJournalStore((s) => s.patientId);
  const activePatientId = useActivePatientStore((s) => s.activeId);
  const { announce } = useShell();
  const { popOut } = useStartApp();
  const openJournal = useOpenJournal();
  const [devOpen, setDevOpen] = useState(false);
  const status = run?.status;
  const navn = run?.navn;
  const ownerId = run?.patient.id;
  let otherId: string | null = null;
  if (ownerId && isStaleFor(ownerId, journalPatientId)) otherId = journalPatientId;
  else if (ownerId && isStaleFor(ownerId, activePatientId)) otherId = activePatientId;
  const { name: otherName, loaded: otherLoaded } = usePatientLabel(otherId);
  const active = useWorkspaceStore((s) => s.current === appTabId(clientId));
  const stale = otherId !== null;
  const ownerName = run ? fullName(run.patient) : "";

  useEffect(() => {
    if (active && status === "running" && navn) {
      announce(copy["live.appRunning"](navn));
    }
  }, [active, status, navn, announce]);

  useEffect(() => {
    if (active && stale && otherLoaded) {
      announce(copy["s5.stale.title"](ownerName, otherName));
    }
  }, [active, stale, otherLoaded, ownerName, otherName, announce]);

  if (!run) return null;
  const patientName = fullName(run.patient);
  const started = format(run.startedAt, "HH:mm");
  const canNavigate = run.status === "running" && !stale;
  const canReload = run.status !== "starting" && run.status !== "session" && !stale;

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
        otherPatientName={otherName}
        onRestart={() => void reloadApp(clientId)}
        onPopOut={() => void popOut(clientId)}
        onClose={() => closeApp(clientId)}
        onOpenJournal={() => otherId && openJournal({ id: otherId })}
      />
      <div className="xp-frame-foot">
        <span className="xp-status-text">{footer}</span>
      </div>
      <DevPanel run={run} hidden={!devOpen} onClose={() => setDevOpen(false)} />
    </>
  );
}
