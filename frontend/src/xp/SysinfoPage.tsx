import { useId } from "react";
import { Badge } from "./Badge";
import { Button } from "./Button";
import { SysinfoRows } from "./SysinfoRows";
import { SysinfoSection } from "./SysinfoSection";
import { XpIcon } from "./XpIcon";
import { copyText } from "./clipboard";
import { copy } from "./copy";
import { useCurrentUser } from "./currentUser";
import {
  buildSummary,
  claimRows,
  fhirBaseOf,
  fhirRows,
  helseIdRows,
  resourceRows,
  smartRows,
} from "./sysinfoModel";
import { useNow } from "./useNow";
import { useSystemInfo } from "./useSystemInfo";

export function SysinfoPage() {
  const { session, smart, fhir } = useSystemInfo();
  const user = useCurrentUser();
  const now = useNow(60_000);
  const helseIdId = useId();
  const claimsId = useId();
  const smartId = useId();
  const fhirId = useId();
  const fhirBase =
    smart.state.status === "ready" ? fhirBaseOf(smart.state.data) : null;
  const sessionUser = user ? { navn: user.navn, hpr: user.hpr } : null;

  function copySummary() {
    const sections = [
      session.state.status === "ready" && {
        title: copy["s11.helseid.title"],
        rows: helseIdRows(session.state.data, sessionUser, now),
      },
      session.state.status === "ready" && {
        title: copy["s11.claims.title"],
        rows: claimRows(session.state.data),
      },
      smart.state.status === "ready" && {
        title: copy["s11.smart.title"],
        rows: smartRows(smart.state.data),
      },
      fhir.state.status === "ready" && {
        title: copy["s11.fhir.title"],
        rows: [
          ...fhirRows(fhir.state.data, fhirBase),
          ...resourceRows(fhir.state.data),
        ],
      },
    ].filter((section) => section !== false);
    void copyText(buildSummary(sections), copy["s11.copied"]);
  }

  return (
    <>
      <div className="xp-pagehead">
        <XpIcon name="systeminfo" size={32} />
        <h1 className="xp-h1">{copy["s11.title"]}</h1>
        <span className="grow" />
        <Button onClick={copySummary}>{copy["s11.copy"]}</Button>
      </div>
      <SysinfoSection
        heading={copy["s11.helseid.title"]}
        headingId={helseIdId}
        state={session.state}
        onRetry={session.retry}
      >
        {(data) => (
          <>
            <div>
              <Badge tone={data.idp === "helseid" ? "ok" : "info"}>
                {data.idp === "helseid"
                  ? copy["s11.helseid.active"]
                  : copy["s11.helseid.local"]}
              </Badge>
            </div>
            <SysinfoRows rows={helseIdRows(data, sessionUser, now)} />
            <h3 id={claimsId} className="xp-h2">
              {copy["s11.claims.title"]}
            </h3>
            <table className="xp-list" aria-labelledby={claimsId}>
              <thead>
                <tr>
                  <th scope="col">
                    <span className="h">{copy["s11.claims.col.claim"]}</span>
                  </th>
                  <th scope="col">
                    <span className="h">{copy["s11.claims.col.value"]}</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {claimRows(data).map(([claim, value]) => (
                  <tr key={claim}>
                    <td className="mono">{claim}</td>
                    <td className="mono">{value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="xp-hint">{copy["s11.claims.note"]}</p>
          </>
        )}
      </SysinfoSection>
      <SysinfoSection
        heading={copy["s11.smart.title"]}
        headingId={smartId}
        state={smart.state}
        onRetry={smart.retry}
      >
        {(data) => (
          <>
            <SysinfoRows rows={smartRows(data)} />
            <p className="xp-hint">{copy["s11.smart.validator"]}</p>
          </>
        )}
      </SysinfoSection>
      <SysinfoSection
        heading={copy["s11.fhir.title"]}
        headingId={fhirId}
        state={fhir.state}
        onRetry={fhir.retry}
      >
        {(data) => (
          <>
            <SysinfoRows rows={fhirRows(data, fhirBase)} />
            <table className="xp-list" aria-label={copy["s11.fhir.title"]}>
              <thead>
                <tr>
                  <th scope="col">
                    <span className="h">{copy["s11.fhir.col.resource"]}</span>
                  </th>
                  <th scope="col">
                    <span className="h">{copy["s11.fhir.col.ops"]}</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {resourceRows(data).map(([type, ops]) => (
                  <tr key={type}>
                    <td>{type}</td>
                    <td>{ops}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </SysinfoSection>
    </>
  );
}
