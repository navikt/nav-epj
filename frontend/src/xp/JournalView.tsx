import { useEffect, useRef } from "react";
import { Button } from "./Button";
import { AppsTab } from "./AppsTab";
import { ConsultationTab } from "./ConsultationTab";
import { Measurements } from "./Measurements";
import { Note } from "./Note";
import { PatientContext } from "./PatientContext";
import { SubTabs } from "./SubTabs";
import { PreviousConsultations } from "./PreviousConsultations";
import { copy } from "./copy";
import { fullName } from "./patientInfo";
import { useShell } from "./shellContext";
import { useAppsStore } from "./appsStore";
import { isDirty, useJournalStore, type JournalSubTab } from "./journalStore";

const ID_PREFIX = "journal";

export function JournalView() {
  const status = useJournalStore((s) => s.status);
  const loadError = useJournalStore((s) => s.loadError);
  const patientId = useJournalStore((s) => s.patientId);
  const patient = useJournalStore((s) => s.patient);
  const consultations = useJournalStore((s) => s.consultations);
  const previousConsultations = consultations.filter(
    (k) => k.status !== "IN_PROGRESS",
  );
  const subTab = useJournalStore((s) => s.subTab);
  const dirty = useJournalStore((s) => isDirty(s));
  const appCount = useAppsStore((s) => s.apps.length);
  const refreshFailed = useJournalStore((s) => s.refreshFailed);
  const { setSubTab, open, refresh } = useJournalStore.getState();
  const { announce } = useShell();
  const announcedFor = useRef<string | null>(null);

  useEffect(() => {
    if (status === "ready" && patient && announcedFor.current !== patient.id) {
      announcedFor.current = patient.id;
      announce(copy["live.journalOpened"](fullName(patient)));
    }
  }, [status, patient, announce]);

  useEffect(() => {
    if (status !== "ready") return;
    const onFocus = () => {
      if (document.visibilityState === "visible") void refresh();
    };
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);
    return () => {
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onFocus);
    };
  }, [status, refresh]);

  if (status === "error") {
    return (
      <div className="xp-note error" role="alert">
        <span>
          {loadError === "consultation"
            ? copy["s4.loadError.consultation"]
            : copy["s4.loadError.patient"]}
        </span>
        {patientId && (
          <Button
            onClick={() =>
              void open(
                patientId,
                useJournalStore.getState().selectedConsultationId ?? undefined,
              )
            }
          >
            {copy["s1.error.retry"]}
          </Button>
        )}
      </div>
    );
  }

  if (status !== "ready" || !patient) {
    return (
      <Note tone="info" role="status">
        {copy["common.loading"]}
      </Note>
    );
  }

  return (
    <>
      <PatientContext patient={patient} />
      {refreshFailed && (
        <div className="xp-note error" role="alert">
          <span>{copy["s4.refreshError"]}</span>
          <Button onClick={() => void refresh()}>{copy["s1.error.retry"]}</Button>
        </div>
      )}
      <SubTabs<JournalSubTab>
        label={copy["s4.tabs.label"]}
        idPrefix={ID_PREFIX}
        selected={subTab}
        onSelect={setSubTab}
        tabs={[
          {
            id: "consultation",
            label: dirty ? copy["s4.tab.consultationDirty"] : copy["s4.tab.consultation"],
          },
          {
            id: "previous",
            label: copy["s4.tab.previous"](previousConsultations.length),
          },
          { id: "measurements", label: copy["s4.tab.measurements"] },
          { id: "apper", label: copy["s4.tab.apper"](appCount) },
        ]}
      >
        {subTab === "consultation" && <ConsultationTab patient={patient} />}
        {subTab === "previous" && (
          <PreviousConsultations consultations={previousConsultations} />
        )}
        {subTab === "measurements" && <Measurements patientId={patient.id} />}
        {subTab === "apper" && <AppsTab />}
      </SubTabs>
    </>
  );
}
