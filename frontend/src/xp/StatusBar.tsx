import { format } from "date-fns";
import { copy } from "./copy";
import { TestMarker } from "./TestMarker";
import { useNow } from "./useNow";

type Props = { onOpenPatients: () => void };

export function StatusBar({ onOpenPatients }: Props) {
  const time = format(useNow(), "HH:mm");
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
      <button
        type="button"
        className="seg"
        aria-label={copy["status.noPatient.aria"]}
        onClick={onOpenPatients}
      >
        {copy["status.noPatient"]}
      </button>
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
