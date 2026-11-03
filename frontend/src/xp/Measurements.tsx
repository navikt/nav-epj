import { useEffect, useId, useState } from "react";
import { Button } from "./Button";
import { Card } from "./Card";
import { Note } from "./Note";
import { copy } from "./copy";
import { fetchMeasurements } from "./api";
import { formatUtcDateTimeInOslo } from "./patientInfo";
import type { Measurement } from "../utils/mapping/epj";

type Result =
  | { key: string; measurements: Measurement[] }
  | { key: string; measurements: null };

type Props = { patientId: string };

export function Measurements({ patientId }: Props) {
  const headingId = useId();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const key = `${patientId}:${attempt}`;

  useEffect(() => {
    let current = true;
    fetchMeasurements(patientId).then(
      (measurements) => {
        if (current) setResult({ key, measurements });
      },
      () => {
        if (current) setResult({ key, measurements: null });
      },
    );
    return () => {
      current = false;
    };
  }, [patientId, key]);

  if (result?.key !== key) {
    return (
      <Note tone="info" role="status">
        {copy["common.loading"]}
      </Note>
    );
  }

  if (result.measurements === null) {
    return (
      <div className="xp-note error" role="alert">
        <span>{copy["s4.measurement.loadError"]}</span>
        <Button onClick={() => setAttempt((n) => n + 1)}>
          {copy["s1.error.retry"]}
        </Button>
      </div>
    );
  }

  if (result.measurements.length === 0) {
    return (
      <div className="xp-card xp-empty">
        <p>
          <b>{copy["s4.measurement.empty"]}</b>
        </p>
      </div>
    );
  }

  const sorted = [...result.measurements].sort((a, b) =>
    b.effectiveAt.localeCompare(a.effectiveAt),
  );

  return (
    <Card heading={copy["s4.measurement.heading"]} headingId={headingId}>
      <div className="xp-tablewrap">
        <table className="xp-list" aria-labelledby={headingId}>
          <thead>
            <tr>
              <th scope="col">
                <span className="h">{copy["s4.measurement.col.name"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s4.measurement.col.value"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s4.measurement.col.time"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s4.measurement.col.status"]}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((m) => (
              <tr key={m.id}>
                <td>
                  {m.loincDisplay} (<code>{m.loincCode}</code>)
                </td>
                <td>
                  {m.value} {m.unitDisplay} (<code>{m.unitCode}</code>)
                </td>
                <td>{formatUtcDateTimeInOslo(m.effectiveAt)}</td>
                <td>{copy[`s4.measurement.status.${m.status}`]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
