import { copy } from "./copy";
import { minutesSince } from "./patientInfo";
import { useNow } from "./useNow";

type Props = { startedAt: string };

export function StatusConsultation({ startedAt }: Props) {
  const now = useNow();
  return (
    <span className="seg">
      {copy["status.consultation"](minutesSince(startedAt, now))}
    </span>
  );
}
