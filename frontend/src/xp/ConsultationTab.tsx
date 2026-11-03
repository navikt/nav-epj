import { CompletedConsultation } from "./CompletedConsultation";
import { NoConsultation } from "./NoConsultation";
import { OngoingConsultation } from "./OngoingConsultation";
import { ongoingOf, useJournalStore } from "./journalStore";
import type { Patient } from "../utils/mapping/ehr";

type Props = { patient: Patient };

export function ConsultationTab({ patient }: Props) {
  const consultations = useJournalStore((s) => s.consultations);
  const selectedId = useJournalStore((s) => s.selectedConsultationId);
  const ongoing = ongoingOf(consultations);
  const selected = selectedId
    ? (consultations.find((k) => k.id === selectedId) ?? null)
    : null;

  if (selected && selected.id !== ongoing?.id) {
    return (
      <CompletedConsultation
        patient={patient}
        consultation={selected}
        canStart={ongoing === null}
      />
    );
  }
  if (ongoing) {
    return <OngoingConsultation patient={patient} consultation={ongoing} />;
  }
  return <NoConsultation patient={patient} />;
}
