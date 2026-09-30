import { differenceInMinutes, format, parseISO } from "date-fns";
import { useAppDialogStore } from "./appDialogStore";
import { isStaleFor, useAppRunStore } from "./appRunStore";
import { copy } from "./copy";
import { ongoingOf, useJournalStore } from "./journalStore";
import { fullName } from "./patientInfo";
import { TestMarker } from "./TestMarker";
import { useNow } from "./useNow";

type Props = {
  onOpenPatients: () => void;
  onOpenJournal?: () => void;
};

export function StatusBar({ onOpenPatients, onOpenJournal }: Props) {
  const now = useNow();
  const time = format(now, "HH:mm");
  const patient = useJournalStore((s) => s.patient);
  const konsultasjoner = useJournalStore((s) => s.konsultasjoner);
  const ongoing = patient ? ongoingOf(konsultasjoner) : null;
  const journalPatientId = useJournalStore((s) => s.patientId);
  const tabApps = useAppRunStore((s) => s.tabApps);
  const staleApps = tabApps.filter((a) =>
    isStaleFor(a.patient.id, journalPatientId),
  );
  const liveApps = tabApps.filter((a) => !staleApps.includes(a));
  const showTabApp = (clientId: string) =>
    useAppDialogStore.getState().show({ kind: "tabApp", clientId });
  return (
    <footer
      className="xp-appstatus"
      aria-label={copy["status.label"]}
      data-xp-landmark="footer"
      tabIndex={-1}
    >
      <span className="seg grow" role="status">
        {copy["status.ready"]}
      </span>
      {patient ? (
        <button
          type="button"
          className="seg"
          aria-label={copy["status.patient.aria"](fullName(patient))}
          onClick={onOpenJournal}
        >
          {copy["status.patient"](fullName(patient))}
        </button>
      ) : (
        <button
          type="button"
          className="seg"
          aria-label={copy["status.noPatient.aria"]}
          onClick={onOpenPatients}
        >
          {copy["status.noPatient"]}
        </button>
      )}
      {ongoing && (
        <span className="seg">
          {copy["status.kons"](
            Math.max(
              0,
              differenceInMinutes(now, parseISO(ongoing.startetTidspunkt)),
            ),
          )}
        </span>
      )}
      {liveApps.length > 0 && (
        <button
          type="button"
          className="seg"
          onClick={() => showTabApp(liveApps[0].clientId)}
        >
          {copy["status.tabApps"](liveApps.length)}
        </button>
      )}
      {staleApps.length > 0 && (
        <button
          type="button"
          className="seg"
          onClick={() => showTabApp(staleApps[0].clientId)}
        >
          {copy["status.staleApps"](staleApps.length)}
        </button>
      )}
      <span className="seg">
        <TestMarker />
      </span>
      <span className="seg">
        <span aria-hidden="true">{time}</span>
        <span className="sr-only">{copy["status.clock.sr"](time)}</span>
      </span>
    </footer>
  );
}
