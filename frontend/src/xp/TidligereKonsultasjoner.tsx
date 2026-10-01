import { useId, useState } from "react";
import { Card } from "./Card";
import { KonsultasjonSummary } from "./KonsultasjonSummary";
import { copy } from "./copy";
import { systemLabel } from "./diagnoseSystem";
import { formatDateTime } from "./patientInfo";
import type { Konsultasjon } from "../utils/mapping/epj";

type Props = { konsultasjoner: Konsultasjon[] };

export function TidligereKonsultasjoner({ konsultasjoner }: Props) {
  const headingId = useId();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = konsultasjoner.find((k) => k.id === selectedId) ?? null;

  if (konsultasjoner.length === 0) {
    return (
      <div className="xp-card xp-empty">
        <p>
          <b>{copy["s4.tidl.empty"]}</b>
        </p>
      </div>
    );
  }

  return (
    <>
      <div className="xp-card xp-tablewrap">
        <table className="xp-list" aria-label={copy["s4.tab.tidl"](konsultasjoner.length)}>
          <thead>
            <tr>
              <th scope="col">
                <span className="h">{copy["s4.tidl.col.date"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s4.tidl.col.status"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s4.tidl.col.diag"]}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {konsultasjoner.map((k) => (
              <tr key={k.id} aria-selected={k.id === selectedId}>
                <td>
                  <button
                    type="button"
                    className="xp-link"
                    aria-pressed={k.id === selectedId}
                    onClick={() => setSelectedId(k.id)}
                  >
                    {formatDateTime(k.startetTidspunkt)}
                  </button>
                </td>
                <td>{copy["s4.tidl.done"]}</td>
                <td>
                  {k.diagnoser.length === 0
                    ? copy["s4.empty.value"]
                    : k.diagnoser
                        .map((d) => `${d.code} (${systemLabel(d.system)})`)
                        .join(", ")}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {selected ? (
        <KonsultasjonSummary
          konsultasjon={selected}
          heading={`${copy["s4.tidl.detail"](formatDateTime(selected.startetTidspunkt))} ${copy["s4.tidl.readonly"]}`}
          headingId={`${headingId}-detail`}
        />
      ) : (
        <Card>
          <p>{copy["s4.tidl.pick"]}</p>
        </Card>
      )}
    </>
  );
}
