import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import {
  ageOn,
  birthDateOf,
  formatDate,
  fullName,
  genderLabel,
  genderOf,
} from "./patientInfo";
import { useNow } from "./useNow";
import {
  JOURNAL_TAB_ID,
  useWorkspaceStore,
  type TabKind,
} from "./workspaceStore";
import { SmartAppsPanel } from "./SmartAppsPanel";
import { TaskLink } from "./TaskLink";
import { TaskPanel } from "./TaskPanel";
import { TASK_PANE_ID } from "./shellContext";

type SystemKind = Extract<TabKind, "kontrollpanel" | "sysinfo" | "hjelp">;

function openSystemTab(kind: SystemKind, label: string) {
  useWorkspaceStore.getState().openTab({ kind, label });
}

type Props = {
  userName?: string;
  patientsCurrent: boolean;
  onOpenPatients: () => void;
  onOpenJournal?: () => void;
  onLogout: () => void;
};

export function TaskPane({
  userName,
  patientsCurrent,
  onOpenPatients,
  onOpenJournal,
  onLogout,
}: Props) {
  const patient = useJournalStore((s) => s.patient);
  const journalCurrent = useWorkspaceStore((s) => s.current === JOURNAL_TAB_ID);
  const current = useWorkspaceStore((s) => s.current);
  const now = useNow();
  const birthDate = patient ? birthDateOf(patient) : null;
  return (
    <nav
      id={TASK_PANE_ID}
      className="xp-taskpane"
      aria-label={copy["nav.label"]}
      data-xp-landmark="nav"
      tabIndex={-1}
    >
      <TaskPanel title={copy["pane.patient.title"]} primary>
        {patient ? (
          <>
            <span className="xp-tp-note">
              <b>{fullName(patient)}</b>
              {birthDate && (
                <>
                  <br />
                  {copy["pane.patient.info"](
                    formatDate(birthDate),
                    ageOn(birthDate, now),
                    genderLabel(genderOf(patient)),
                  )}
                </>
              )}
            </span>
            <TaskLink
              icon="journal"
              label={copy["pane.patient.openJournal"]}
              current={journalCurrent}
              onActivate={onOpenJournal}
            />
            <TaskLink
              icon="sok"
              label={copy["pane.patient.findOther"]}
              onActivate={onOpenPatients}
            />
          </>
        ) : (
          <>
            <span className="xp-tp-note">{copy["pane.patient.none"]}</span>
            <TaskLink
              icon="sok"
              label={copy["pane.patient.find"]}
              onActivate={onOpenPatients}
            />
          </>
        )}
      </TaskPanel>
      <SmartAppsPanel />
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
          current={current === "kontrollpanel"}
          onActivate={() =>
            openSystemTab("kontrollpanel", copy["pane.system.kontroll"])
          }
        />
        <TaskLink
          icon="systeminfo"
          label={copy["pane.system.sysinfo"]}
          current={current === "sysinfo"}
          onActivate={() =>
            openSystemTab("sysinfo", copy["pane.system.sysinfo"])
          }
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
          current={current === "hjelp"}
          onActivate={() => openSystemTab("hjelp", copy["pane.system.hjelp"])}
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
    </nav>
  );
}
