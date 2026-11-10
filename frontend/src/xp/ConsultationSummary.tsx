import type { ReactNode } from "react";
import { Card } from "./Card";
import { ConsultationDocuments } from "./ConsultationDocuments";
import { copy } from "./copy";
import { systemLabel } from "./diagnosisSystem";
import { noteOf } from "./journalStore";
import { formatDateTime } from "./patientInfo";
import type { Consultation } from "../utils/mapping/ehr";

type Props = {
  consultation: Consultation;
  heading: string;
  headingId: string;
  children?: ReactNode;
};

export function ConsultationSummary({
  consultation,
  heading,
  headingId,
  children,
}: Props) {
  const note = noteOf(consultation);
  const [main, ...secondaries] = consultation.diagnoses;
  return (
    <Card heading={heading} headingId={headingId}>
      {children}
      <dl className="xp-dl">
        <dt>{copy["s4.info.started"]}</dt>
        <dd>{formatDateTime(consultation.startedAt)}</dd>
        {consultation.endedAt && (
          <>
            <dt>{copy["s4.done.ended"]}</dt>
            <dd>{formatDateTime(consultation.endedAt)}</dd>
          </>
        )}
        <dt>{copy["s4.done.main"]}</dt>
        <dd>
          {main ? (
            <>
              <code>{main.code}</code> {main.text} · {systemLabel(main.system)}
            </>
          ) : (
            copy["s4.empty.value"]
          )}
        </dd>
        <dt>{copy["s4.done.secondary"]}</dt>
        <dd>
          {secondaries.length === 0 ? (
            copy["s4.empty.value"]
          ) : (
            <ul>
              {secondaries.map((d) => (
                <li key={`${d.system}:${d.code}`}>
                  <code>{d.code}</code> {d.text} · {systemLabel(d.system)}
                </li>
              ))}
            </ul>
          )}
        </dd>
        <dt>{copy["s4.note.label"]}</dt>
        <dd>
          {note === "" ? (
            copy["s4.empty.value"]
          ) : (
            <p className="xp-prewrap">{note}</p>
          )}
        </dd>
      </dl>
      <ConsultationDocuments consultation={consultation} />
    </Card>
  );
}
