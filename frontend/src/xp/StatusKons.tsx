import { differenceInMinutes, parseISO } from "date-fns";
import { copy } from "./copy";
import { useNow } from "./useNow";

type Props = { startetTidspunkt: string };

export function StatusKons({ startetTidspunkt }: Props) {
  const now = useNow();
  return (
    <span className="seg">
      {copy["status.kons"](
        Math.max(0, differenceInMinutes(now, parseISO(startetTidspunkt))),
      )}
    </span>
  );
}
