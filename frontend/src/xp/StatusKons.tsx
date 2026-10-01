import { copy } from "./copy";
import { minutesSince } from "./patientInfo";
import { useNow } from "./useNow";

type Props = { startetTidspunkt: string };

export function StatusKons({ startetTidspunkt }: Props) {
  const now = useNow();
  return (
    <span className="seg">
      {copy["status.kons"](minutesSince(startetTidspunkt, now))}
    </span>
  );
}
