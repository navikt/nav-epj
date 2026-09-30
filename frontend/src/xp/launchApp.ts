import { ApiError, LaunchError, launchApp } from "./api";
import { appTabId, initialsOf, statusText } from "./appInfo";
import { useAppDialogStore, type AppErrorCode } from "./appDialogStore";
import { useAppRunStore } from "./appRunStore";
import { useAppsStore } from "./appsStore";
import { useBalloonStore } from "./balloonStore";
import { copy } from "./copy";
import { ongoingOf, useJournalStore } from "./journalStore";
import { useLaunchModeStore, type LaunchChoice } from "./launchModeStore";
import { fullName } from "./patientInfo";
import { createSession } from "./session";
import { useWorkspaceStore } from "./workspaceStore";
import type { App, Konsultasjon, Pasient } from "../utils/mapping/epj";

type LaunchContext = {
  patient: Pasient;
  konsultasjon: Pick<Konsultasjon, "id" | "startetTidspunkt">;
};

export type LaunchResult = "iframe" | "tab" | null;

const LAUNCH_CALL = "POST /api/launch";
const sessions = new Map<string, ReturnType<typeof createSession>>();

function beginLaunch(clientId: string) {
  let session = sessions.get(clientId);
  if (!session) {
    session = createSession();
    sessions.set(clientId, session);
  }
  session.next();
  return session.capture();
}

function cancelLaunch(clientId: string) {
  sessions.get(clientId)?.next();
}

export function resetLaunches() {
  sessions.clear();
}

function showError(
  code: AppErrorCode,
  app: { clientId: string | null; navn: string },
  patient: string,
  extra: { status?: number | null; call?: string; retry?: () => void } = {},
) {
  useAppDialogStore.getState().show({
    kind: "error",
    code,
    clientId: app.clientId,
    app: app.navn,
    patientName: patient,
    status: extra.status ?? null,
    call: extra.call ?? "",
    at: new Date(),
    retry: extra.retry,
  });
}

function reportLaunchFailure(
  error: unknown,
  app: App,
  patient: string,
  retry: () => void,
) {
  if (error instanceof ApiError && error.status === 401) return;
  const extra = {
    status: error instanceof ApiError ? error.status : null,
    call: LAUNCH_CALL,
  };
  if (error instanceof LaunchError) {
    showError(error.code, app, patient, extra);
  } else {
    showError("NETWORK", app, patient, { ...extra, retry });
  }
}

function readContext(app: App): LaunchContext | null {
  const { patient, konsultasjoner } = useJournalStore.getState();
  if (!patient) {
    showError("NO_ACTIVE_PATIENT", app, "");
    return null;
  }
  const ongoing = ongoingOf(konsultasjoner);
  if (!ongoing) {
    showError("NO_ACTIVE_ENCOUNTER", app, fullName(patient));
    return null;
  }
  return {
    patient,
    konsultasjon: { id: ongoing.id, startetTidspunkt: ongoing.startetTidspunkt },
  };
}

const patientChanged = (patient: Pasient) =>
  useJournalStore.getState().patientId !== patient.id;

export function findApp(clientId: string) {
  return useAppsStore.getState().apps.find((a) => a.clientId === clientId);
}

export function closeApp(clientId: string) {
  cancelLaunch(clientId);
  useAppRunStore.getState().removeRun(clientId);
  useWorkspaceStore.getState().closeTab(appTabId(clientId));
}

async function launchInFrame(app: App, context: LaunchContext) {
  const runs = useAppRunStore.getState();
  const isCurrent = beginLaunch(app.clientId);
  runs.startRun({
    clientId: app.clientId,
    navn: app.navn,
    patient: context.patient,
    konsultasjon: context.konsultasjon,
    startedAt: new Date(),
  });
  useWorkspaceStore.getState().openTab({
    kind: "app",
    clientId: app.clientId,
    label: copy["tabs.app"](app.navn, initialsOf(fullName(context.patient))),
    ariaLabel: copy["tabs.app.aria"](
      app.navn,
      fullName(context.patient),
      copy["s5.status.starting"],
    ),
  });
  let url: string;
  try {
    url = await launchApp(app.clientId);
  } catch (error) {
    if (!isCurrent()) return null;
    closeApp(app.clientId);
    reportLaunchFailure(error, app, fullName(context.patient), () =>
      void startApp(app, "iframe"),
    );
    return null;
  }
  if (!isCurrent()) return null;
  if (patientChanged(context.patient)) {
    closeApp(app.clientId);
    return null;
  }
  runs.setLaunchUrl(app.clientId, url);
  runs.addEvent(app.clientId, { kind: "launch", status: 200 });
  return "iframe" as const;
}

async function launchInTab(
  app: App,
  context: LaunchContext,
  balloonBody: string,
) {
  const isCurrent = beginLaunch(app.clientId);
  let url: string;
  try {
    url = await launchApp(app.clientId);
  } catch (error) {
    if (!isCurrent()) return null;
    reportLaunchFailure(error, app, fullName(context.patient), () =>
      void startApp(app, "tab"),
    );
    return null;
  }
  if (!isCurrent() || patientChanged(context.patient)) return null;
  window.open(url, `smart-${app.clientId}-${Date.now()}`, "noopener,noreferrer");
  useAppRunStore.getState().addTabApp({
    clientId: app.clientId,
    navn: app.navn,
    patient: context.patient,
    startedAt: new Date(),
  });
  useBalloonStore.getState().show({
    title: copy["s5.popout.balloon.title"](app.navn),
    body: balloonBody,
    icon: "ny-fane",
  });
  return "tab" as const;
}

export async function startApp(
  app: App,
  choice?: LaunchChoice,
): Promise<LaunchResult> {
  const running = useAppRunStore
    .getState()
    .runs.some((r) => r.clientId === app.clientId);
  if (running) {
    useWorkspaceStore.getState().setCurrent(appTabId(app.clientId));
    return null;
  }
  const context = readContext(app);
  if (!context) return null;
  const mode =
    choice ??
    (app.launchMode === "ask"
      ? useLaunchModeStore.getState().choices[app.clientId]
      : app.launchMode);
  if (!mode) {
    useAppDialogStore.getState().show({ kind: "ask", app });
    return null;
  }
  return mode === "iframe"
    ? launchInFrame(app, context)
    : launchInTab(app, context, copy["pane.apps.runningTab"]);
}

export async function reloadApp(clientId: string) {
  const run = useAppRunStore.getState().runs.find((r) => r.clientId === clientId);
  const app = findApp(clientId);
  if (!run || !app || patientChanged(run.patient)) return;
  const runs = useAppRunStore.getState();
  const isCurrent = beginLaunch(clientId);
  runs.restartRun(clientId, new Date());
  try {
    const url = await launchApp(clientId);
    if (!isCurrent()) return;
    runs.setLaunchUrl(clientId, url);
    runs.addEvent(clientId, { kind: "launch", status: 200 });
  } catch (error) {
    if (!isCurrent()) return;
    if (error instanceof ApiError && error.status === 401) return;
    runs.setStatus(clientId, "error");
    reportLaunchFailure(error, app, fullName(run.patient), () =>
      void reloadApp(clientId),
    );
  }
}

export async function popOutApp(clientId: string): Promise<LaunchResult> {
  const run = useAppRunStore.getState().runs.find((r) => r.clientId === clientId);
  const app = findApp(clientId);
  if (!run || !app) return null;
  closeApp(clientId);
  return launchInTab(
    app,
    { patient: run.patient, konsultasjon: run.konsultasjon },
    copy["s5.popout.balloon.body"],
  );
}

export function reportTimeout(clientId: string, origin: string) {
  const run = useAppRunStore.getState().runs.find((r) => r.clientId === clientId);
  if (!run) return;
  useAppRunStore.getState().setStatus(clientId, "timeout");
  showError("FRAMING_REFUSED", { clientId, navn: run.navn }, fullName(run.patient), {
    call: origin,
  });
}

export function closeStaleRuns(journalPatientId: string) {
  for (const run of useAppRunStore.getState().runs) {
    if (run.patient.id !== journalPatientId) closeApp(run.clientId);
  }
}

export function syncRunTabs() {
  const { tabs, updateTab } = useWorkspaceStore.getState();
  const { runs } = useAppRunStore.getState();
  const journalPatientId = useJournalStore.getState().patientId;
  for (const run of runs) {
    const id = appTabId(run.clientId);
    const tab = tabs.find((t) => t.id === id);
    if (!tab) continue;
    const stale =
      journalPatientId !== null && journalPatientId !== run.patient.id;
    const ariaLabel = copy["tabs.app.aria"](
      run.navn,
      fullName(run.patient),
      statusText(run.status, stale, run),
    );
    const error = run.status === "timeout" || run.status === "error";
    if (tab.ariaLabel !== ariaLabel || tab.error !== error) {
      updateTab(id, { ariaLabel, error });
    }
  }
}

export function dropClosedTabRuns() {
  const { tabs } = useWorkspaceStore.getState();
  for (const run of useAppRunStore.getState().runs) {
    if (!tabs.some((t) => t.id === appTabId(run.clientId))) {
      cancelLaunch(run.clientId);
      useAppRunStore.getState().removeRun(run.clientId);
    }
  }
}
