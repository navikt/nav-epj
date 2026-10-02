import type { ReactNode } from "react";
import { Card } from "./Card";
import { copy } from "./copy";
import { systemLabel } from "./diagnoseSystem";
import { noteOf } from "./journalStore";
import { formatDateTime } from "./patientInfo";
import type { Konsultasjon } from "../utils/mapping/epj";

type Props = {
  konsultasjon: Konsultasjon;
  heading: string;
  headingId: string;
  children?: ReactNode;
};

export function KonsultasjonSummary({
  konsultasjon,
  heading,
  headingId,
  children,
}: Props) {
  const note = noteOf(konsultasjon);
  return (
    <Card heading={heading} headingId={headingId}>
      {children}
      <dl className="xp-dl">
        <dt>{copy["s4.info.started"]}</dt>
        <dd>{formatDateTime(konsultasjon.startetTidspunkt)}</dd>
        {konsultasjon.avsluttetTidspunkt && (
          <>
            <dt>{copy["s4.done.ended"]}</dt>
            <dd>{formatDateTime(konsultasjon.avsluttetTidspunkt)}</dd>
          </>
        )}
        <dt>{copy["s4.done.diag"]}</dt>
        <dd>
          {konsultasjon.diagnoser.length === 0 ? (
            copy["s4.diag.none"]
          ) : (
            <ul>
              {konsultasjon.diagnoser.map((d) => (
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
    </Card>
  );
}
