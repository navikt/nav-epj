import { useId, useState } from "react";
import { Card } from "./Card";
import { ConsultationSummary } from "./ConsultationSummary";
import { copy } from "./copy";
import { systemLabel } from "./diagnosisSystem";
import { formatDateTime } from "./patientInfo";
import type { Consultation } from "../utils/mapping/ehr";

type Props = { consultations: Consultation[] };

const diagnosisCell = (d: Consultation["diagnoses"][number]) =>
  `${d.code} (${systemLabel(d.system)})`;

export function PreviousConsultations({ consultations }: Props) {
  const headingId = useId();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = consultations.find((k) => k.id === selectedId) ?? null;

  if (consultations.length === 0) {
    return (
      <div className="xp-card xp-empty">
        <p>
          <b>{copy["s4.previous.empty"]}</b>
        </p>
      </div>
    );
  }

  return (
    <>
      <div className="xp-card xp-tablewrap">
        <table className="xp-list" aria-label={copy["s4.tab.previous"](consultations.length)}>
          <thead>
            <tr>
              <th scope="col">
                <span className="h">{copy["s4.previous.col.date"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s4.previous.col.status"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s4.previous.col.main"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s4.previous.col.secondary"]}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {consultations.map((k) => {
              const [main, ...secondaries] = k.diagnoses;
              return (
              <tr key={k.id} aria-selected={k.id === selectedId}>
                <td>
                  <button
                    type="button"
                    className="xp-link"
                    aria-pressed={k.id === selectedId}
                    onClick={() => setSelectedId(k.id)}
                  >
                    {formatDateTime(k.startedAt)}
                  </button>
                </td>
                <td>
                  {k.status === "CANCELLED"
                    ? copy["s4.previous.cancelled"]
                    : copy["s4.previous.done"]}
                </td>
                <td>{main ? diagnosisCell(main) : copy["s4.empty.value"]}</td>
                <td>
                  {secondaries.length === 0
                    ? copy["s4.empty.value"]
                    : secondaries.map(diagnosisCell).join(", ")}
                </td>
              </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {selected ? (
        <ConsultationSummary
          consultation={selected}
          heading={`${copy["s4.previous.detail"](formatDateTime(selected.startedAt))} ${copy["s4.previous.readonly"]}`}
          headingId={`${headingId}-detail`}
        />
      ) : (
        <Card>
          <p>{copy["s4.previous.pick"]}</p>
        </Card>
      )}
    </>
  );
}
