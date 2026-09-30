import { format } from "date-fns";
import { copy } from "./copy";
import { useNow } from "./useNow";

export function StatusClock() {
  const time = format(useNow(), "HH:mm");
  return (
    <>
      <span aria-hidden="true">{time}</span>
      <span className="sr-only">{copy["status.clock.sr"](time)}</span>
    </>
  );
}
