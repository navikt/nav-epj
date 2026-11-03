import { useId } from "react";
import { Button } from "./Button";
import { ConsultationSummary } from "./ConsultationSummary";
import { Note } from "./Note";
import { copy } from "./copy";
import { fullName } from "./patientInfo";
import { useJournalStore } from "./journalStore";
import { useShell } from "./shellContext";
import type { Consultation, Patient } from "../utils/mapping/ehr";

type Props = {
  patient: Patient;
  consultation: Consultation;
  canStart: boolean;
};

export function CompletedConsultation({ patient, consultation, canStart }: Props) {
  const headingId = useId();
  const starting = useJournalStore((s) => s.starting);
  const startFailed = useJournalStore((s) => s.startFailed);
  const { announce } = useShell();

  async function start() {
    if (starting) return;
    await useJournalStore.getState().start();
    if (!useJournalStore.getState().startFailed) {
      announce(copy["live.consultationStarted"](fullName(patient)));
    }
  }

  return (
    <ConsultationSummary
      consultation={consultation}
      heading={copy["s4.done.title"]}
      headingId={headingId}
    >
      <Note tone="info">{copy["s4.done.note"]}</Note>
      {startFailed && (
        <Note tone="error" role="alert">
          {copy["s4.loadError.consultation"]}
        </Note>
      )}
      {canStart && (
        <div className="xp-form-actions">
          <Button isDefault aria-disabled={starting} onClick={() => void start()}>
            {copy["s4.start"]}
          </Button>
        </div>
      )}
    </ConsultationSummary>
  );
}
