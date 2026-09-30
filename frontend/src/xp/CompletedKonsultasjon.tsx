import { useId } from "react";
import { Button } from "./Button";
import { KonsultasjonSummary } from "./KonsultasjonSummary";
import { Note } from "./Note";
import { copy } from "./copy";
import { fullName } from "./patientInfo";
import { useJournalStore } from "./journalStore";
import { useShell } from "./shellContext";
import type { Konsultasjon, Pasient } from "../utils/mapping/epj";

type Props = {
  patient: Pasient;
  konsultasjon: Konsultasjon;
  canStart: boolean;
};

export function CompletedKonsultasjon({ patient, konsultasjon, canStart }: Props) {
  const headingId = useId();
  const starting = useJournalStore((s) => s.starting);
  const startFailed = useJournalStore((s) => s.startFailed);
  const { announce } = useShell();

  async function start() {
    if (starting) return;
    await useJournalStore.getState().start();
    if (!useJournalStore.getState().startFailed) {
      announce(copy["live.konsStarted"](fullName(patient)));
    }
  }

  return (
    <KonsultasjonSummary
      konsultasjon={konsultasjon}
      heading={copy["s4.done.title"]}
      headingId={headingId}
    >
      <Note tone="info">{copy["s4.done.note"]}</Note>
      {startFailed && (
        <Note tone="error" role="alert">
          {copy["s4.loadError.kons"]}
        </Note>
      )}
      {canStart && (
        <div className="xp-form-actions">
          <Button isDefault aria-disabled={starting} onClick={() => void start()}>
            {copy["s4.start"]}
          </Button>
        </div>
      )}
    </KonsultasjonSummary>
  );
}
