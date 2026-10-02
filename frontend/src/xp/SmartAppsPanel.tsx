import { useId } from "react";
import { TaskLink } from "./TaskLink";
import { TaskPanel } from "./TaskPanel";
import { appIconName, appTabId } from "./appInfo";
import { useAppDialogStore } from "./appDialogStore";
import { useActivePatientStore } from "./activePatientStore";
import { isOutdatedFor, isStaleFor, useAppRunStore } from "./appRunStore";
import { useAppsStore } from "./appsStore";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import { useAppsEnabled } from "./useAppsEnabled";
import { useStartApp } from "./useStartApp";
import { useWorkspaceStore } from "./workspaceStore";

const modeLabels = {
  iframe: copy["pane.apps.mode.iframe"],
  tab: copy["pane.apps.mode.tab"],
  ask: copy["pane.apps.mode.ask"],
} as const;

export function SmartAppsPanel() {
  const apps = useAppsStore((s) => s.apps);
  const runs = useAppRunStore((s) => s.runs);
  const tabApps = useAppRunStore((s) => s.tabApps);
  const current = useWorkspaceStore((s) => s.current);
  const journalPatientId = useJournalStore((s) => s.patientId);
  const activePatientId = useActivePatientStore((s) => s.activeId);
  const enabled = useAppsEnabled();
  const reasonId = useId();
  const { start } = useStartApp();

  return (
    <TaskPanel title={copy["pane.apps.title"]}>
      {!enabled && (
        <span className="xp-tp-note" id={reasonId}>
          {copy["pane.apps.disabledReason"]}
        </span>
      )}
      {apps.map((app) => {
        const run = runs.find((r) => r.clientId === app.clientId);
        const running = run !== undefined;
        const outdated =
          run !== undefined &&
          isOutdatedFor(run.patient.id, journalPatientId, activePatientId);
        return (
          <TaskLink
            key={app.clientId}
            icon={appIconName(app.ikon)}
            label={app.navn}
            sub={modeLabels[app.launchMode]}
            badge={
              outdated
                ? copy["context.stale"]
                : running
                  ? copy["pane.apps.runningHost"]
                  : undefined
            }
            stale={outdated}
            current={current === appTabId(app.clientId)}
            disabled={!enabled && !running}
            describedBy={enabled || running ? undefined : reasonId}
            onActivate={() => void start(app)}
          />
        );
      })}
      {tabApps.map((tabApp) => {
        const stale = isStaleFor(tabApp.patient.id, journalPatientId);
        return (
          <TaskLink
            key={tabApp.id}
            icon="ny-fane"
            label={tabApp.navn}
            sub={stale ? copy["pane.apps.stale"] : copy["pane.apps.runningTab"]}
            stale={stale}
            onActivate={() =>
              useAppDialogStore
                .getState()
                .show({ kind: "tabApp", tabId: tabApp.id })
            }
          />
        );
      })}
    </TaskPanel>
  );
}
