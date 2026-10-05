import { useEffect, useRef } from "react";
import { Button } from "./Button";
import { AppsTab } from "./AppsTab";
import { KonsultasjonTab } from "./KonsultasjonTab";
import { Maalinger } from "./Maalinger";
import { Note } from "./Note";
import { PatientContext } from "./PatientContext";
import { SubTabs } from "./SubTabs";
import { TidligereKonsultasjoner } from "./TidligereKonsultasjoner";
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
  const konsultasjoner = useJournalStore((s) => s.konsultasjoner);
  const tidligereKonsultasjoner = konsultasjoner.filter(
    (k) => k.status !== "PÅGÅENDE",
  );
  const subTab = useJournalStore((s) => s.subTab);
  const dirty = useJournalStore((s) => isDirty(s));
  const appCount = useAppsStore((s) => s.apps.length);
  const { setSubTab, open } = useJournalStore.getState();
  const { announce } = useShell();
  const announcedFor = useRef<string | null>(null);

  useEffect(() => {
    if (status === "ready" && patient && announcedFor.current !== patient.id) {
      announcedFor.current = patient.id;
      announce(copy["live.journalOpened"](fullName(patient)));
    }
  }, [status, patient, announce]);

  if (status === "error") {
    return (
      <div className="xp-note error" role="alert">
        <span>
          {loadError === "kons"
            ? copy["s4.loadError.kons"]
            : copy["s4.loadError.patient"]}
        </span>
        {patientId && (
          <Button
            onClick={() =>
              void open(
                patientId,
                useJournalStore.getState().selectedKonsultasjonId ?? undefined,
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
      <PatientContext pasient={patient} />
      <SubTabs<JournalSubTab>
        label={copy["s4.tabs.label"]}
        idPrefix={ID_PREFIX}
        selected={subTab}
        onSelect={setSubTab}
        tabs={[
          {
            id: "konsultasjon",
            label: dirty ? copy["s4.tab.konsDirty"] : copy["s4.tab.kons"],
          },
          {
            id: "tidligere",
            label: copy["s4.tab.tidl"](tidligereKonsultasjoner.length),
          },
          { id: "maalinger", label: copy["s4.tab.maalinger"] },
          { id: "apper", label: copy["s4.tab.apper"](appCount) },
        ]}
      >
        {subTab === "konsultasjon" && <KonsultasjonTab patient={patient} />}
        {subTab === "tidligere" && (
          <TidligereKonsultasjoner konsultasjoner={tidligereKonsultasjoner} />
        )}
        {subTab === "maalinger" && <Maalinger patientId={patient.id} />}
        {subTab === "apper" && <AppsTab />}
      </SubTabs>
    </>
  );
}
