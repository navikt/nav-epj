import { CompletedKonsultasjon } from "./CompletedKonsultasjon";
import { NoKonsultasjon } from "./NoKonsultasjon";
import { OngoingKonsultasjon } from "./OngoingKonsultasjon";
import { ongoingOf, useJournalStore } from "./journalStore";
import type { Pasient } from "../utils/mapping/epj";

type Props = { patient: Pasient };

export function KonsultasjonTab({ patient }: Props) {
  const konsultasjoner = useJournalStore((s) => s.konsultasjoner);
  const selectedId = useJournalStore((s) => s.selectedKonsultasjonId);
  const ongoing = ongoingOf(konsultasjoner);
  const selected = selectedId
    ? (konsultasjoner.find((k) => k.id === selectedId) ?? null)
    : null;

  if (selected && selected.id !== ongoing?.id) {
    return (
      <CompletedKonsultasjon
        patient={patient}
        konsultasjon={selected}
        canStart={ongoing === null}
      />
    );
  }
  if (ongoing) {
    return <OngoingKonsultasjon patient={patient} konsultasjon={ongoing} />;
  }
  return <NoKonsultasjon patient={patient} />;
}
