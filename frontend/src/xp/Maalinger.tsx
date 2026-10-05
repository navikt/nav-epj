import { useEffect, useId, useState } from "react";
import { Button } from "./Button";
import { Card } from "./Card";
import { Note } from "./Note";
import { copy } from "./copy";
import { fetchMaalinger } from "./api";
import { formatUtcDateTimeInOslo } from "./patientInfo";
import type { Maaling } from "../utils/mapping/epj";

type Result =
  | { key: string; maalinger: Maaling[] }
  | { key: string; maalinger: null };

type Props = { patientId: string };

export function Maalinger({ patientId }: Props) {
  const headingId = useId();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const key = `${patientId}:${attempt}`;

  useEffect(() => {
    let current = true;
    fetchMaalinger(patientId).then(
      (maalinger) => {
        if (current) setResult({ key, maalinger });
      },
      () => {
        if (current) setResult({ key, maalinger: null });
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

  if (result.maalinger === null) {
    return (
      <div className="xp-note error" role="alert">
        <span>{copy["s4.maal.loadError"]}</span>
        <Button onClick={() => setAttempt((n) => n + 1)}>
          {copy["s1.error.retry"]}
        </Button>
      </div>
    );
  }

  if (result.maalinger.length === 0) {
    return (
      <div className="xp-card xp-empty">
        <p>
          <b>{copy["s4.maal.empty"]}</b>
        </p>
      </div>
    );
  }

  const sorted = [...result.maalinger].sort((a, b) =>
    b.effektivTidspunkt.localeCompare(a.effektivTidspunkt),
  );

  return (
    <Card heading={copy["s4.maal.heading"]} headingId={headingId}>
      <div className="xp-tablewrap">
        <table className="xp-list" aria-labelledby={headingId}>
          <thead>
            <tr>
              <th scope="col">
                <span className="h">{copy["s4.maal.col.name"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s4.maal.col.value"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s4.maal.col.time"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s4.maal.col.status"]}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((m) => (
              <tr key={m.id}>
                <td>
                  {m.loincVisningsnavn} (<code>{m.loincKode}</code>)
                </td>
                <td>
                  {m.verdi} {m.enhetVisningsnavn} (<code>{m.enhetKode}</code>)
                </td>
                <td>{formatUtcDateTimeInOslo(m.effektivTidspunkt)}</td>
                <td>{copy[`s4.maal.status.${m.status}`]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
