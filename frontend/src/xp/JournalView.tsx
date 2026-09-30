import { Button } from "./Button";
import { KonsultasjonTab } from "./KonsultasjonTab";
import { Note } from "./Note";
import { PatientContext } from "./PatientContext";
import { SubTabs } from "./SubTabs";
import { TidligereKonsultasjoner } from "./TidligereKonsultasjoner";
import { copy } from "./copy";
import { isDirty, useJournalStore, type JournalSubTab } from "./journalStore";

const ID_PREFIX = "journal";

export function JournalView() {
  const status = useJournalStore((s) => s.status);
  const loadError = useJournalStore((s) => s.loadError);
  const patientId = useJournalStore((s) => s.patientId);
  const patient = useJournalStore((s) => s.patient);
  const konsultasjoner = useJournalStore((s) => s.konsultasjoner);
  const subTab = useJournalStore((s) => s.subTab);
  const dirty = useJournalStore((s) => isDirty(s));
  const { setSubTab, open } = useJournalStore.getState();

  if (status === "error") {
    return (
      <div className="xp-note error" role="alert">
        <span>
          {loadError === "kons"
            ? copy["s4.loadError.kons"]
            : copy["s4.loadError.patient"]}
        </span>
        {patientId && (
          <Button onClick={() => void open(patientId)}>
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
            label: copy["s4.tab.tidl"](konsultasjoner.length),
          },
        ]}
      >
        {subTab === "konsultasjon" ? (
          <KonsultasjonTab patient={patient} />
        ) : (
          <TidligereKonsultasjoner konsultasjoner={konsultasjoner} />
        )}
      </SubTabs>
    </>
  );
}
