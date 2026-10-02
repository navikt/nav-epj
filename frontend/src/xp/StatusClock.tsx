import { copy } from "./copy";
import { formatTime } from "./patientInfo";
import { useNow } from "./useNow";

export function StatusClock() {
  const time = formatTime(useNow());
  return (
    <>
      <span aria-hidden="true">{time}</span>
      <span className="sr-only">{copy["status.clock.sr"](time)}</span>
    </>
  );
}
