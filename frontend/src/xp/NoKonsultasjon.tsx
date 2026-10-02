import { useId } from "react";
import { Button } from "./Button";
import { Card } from "./Card";
import { Note } from "./Note";
import { copy } from "./copy";
import { fullName } from "./patientInfo";
import { useJournalStore } from "./journalStore";
import { useShell } from "./shellContext";
import type { Pasient } from "../utils/mapping/epj";

type Props = { patient: Pasient };

export function NoKonsultasjon({ patient }: Props) {
  const headingId = useId();
  const starting = useJournalStore((s) => s.starting);
  const startFailed = useJournalStore((s) => s.startFailed);
  const { announce } = useShell();
  const name = fullName(patient);

  async function start() {
    if (starting) return;
    await useJournalStore.getState().start();
    if (!useJournalStore.getState().startFailed) {
      announce(copy["live.konsStarted"](name));
    }
  }

  return (
    <Card heading={copy["s4.none.title"]} headingId={headingId}>
      <p>{copy["s4.none.body"](name)}</p>
      {startFailed && (
        <Note tone="error" role="alert">
          {copy["s4.loadError.kons"]}
        </Note>
      )}
      <div className="xp-form-actions">
        <Button isDefault aria-disabled={starting} onClick={() => void start()}>
          {copy["s4.start"]}
        </Button>
      </div>
    </Card>
  );
}
