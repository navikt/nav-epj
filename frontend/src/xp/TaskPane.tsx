import { copy } from "./copy";
import { AppearancePanel } from "./AppearancePanel";
import { TaskLink } from "./TaskLink";
import { TaskPanel } from "./TaskPanel";
import { TASK_PANE_ID } from "./shellContext";

type Props = {
  userName?: string;
  patientsCurrent: boolean;
  onOpenPatients: () => void;
  onLogout: () => void;
};

export function TaskPane({
  userName,
  patientsCurrent,
  onOpenPatients,
  onLogout,
}: Props) {
  return (
    <nav
      id={TASK_PANE_ID}
      className="xp-taskpane"
      aria-label={copy["nav.label"]}
      data-xp-landmark="nav"
      tabIndex={-1}
    >
      <TaskPanel title={copy["pane.patient.title"]} primary>
        <span className="xp-tp-note">{copy["pane.patient.none"]}</span>
        <TaskLink
          icon="sok"
          label={copy["pane.patient.find"]}
          onActivate={onOpenPatients}
        />
      </TaskPanel>
      <TaskPanel title={copy["pane.apps.title"]}>
        <span className="xp-tp-note">{copy["pane.apps.disabledReason"]}</span>
      </TaskPanel>
      <TaskPanel title={copy["pane.system.title"]}>
        <TaskLink
          icon="pasienter"
          label={copy["pane.system.patients"]}
          current={patientsCurrent}
          onActivate={onOpenPatients}
        />
        <TaskLink
          icon="kontrollpanel"
          label={copy["pane.system.kontroll"]}
          badge={copy["pane.soon.label"]}
          disabled
        />
        <TaskLink
          icon="systeminfo"
          label={copy["pane.system.sysinfo"]}
          badge={copy["pane.soon.label"]}
          disabled
        />
        <TaskLink
          icon="hendelseslogg"
          label={copy["pane.system.logg"]}
          badge={copy["badge.phase2"]}
          disabled
        />
        <TaskLink
          icon="hjelp"
          label={copy["pane.system.hjelp"]}
          badge={copy["pane.soon.label"]}
          disabled
        />
        {userName && (
          <TaskLink
            icon="loggav"
            label={copy["pane.system.logoutNarrow"](userName)}
            narrowOnly
            onActivate={onLogout}
          />
        )}
      </TaskPanel>
      <TaskPanel title={copy["pane.soon.title"]}>
        <TaskLink
          icon="kommer"
          label={copy["pane.soon.kj"]}
          badge={copy["pane.soon.label"]}
          disabled
        />
        <TaskLink
          icon="kommer"
          label={copy["pane.soon.er"]}
          badge={copy["pane.soon.label"]}
          disabled
        />
        <TaskLink
          icon="kommer"
          label={copy["pane.soon.em"]}
          badge={copy["pane.soon.label"]}
          disabled
        />
      </TaskPanel>
      <AppearancePanel />
    </nav>
  );
}
