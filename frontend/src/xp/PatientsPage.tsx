import { useEffect, useMemo } from "react";
import { Badge } from "./Badge";
import { Button } from "./Button";
import { IconButton } from "./IconButton";
import { PatientsPager } from "./PatientsPager";
import { SegmentedControl } from "./SegmentedControl";
import { XpIcon } from "./XpIcon";
import { copy } from "./copy";
import { filterPatients } from "./patientFilter";
import {
  ageOn,
  birthDateOf,
  formatDate,
  fullName,
  maskPersonident,
  personidentTail,
} from "./patientInfo";
import {
  PAGE_SIZE,
  scopePatients,
  usePatientsStore,
  type PatientsView,
} from "./patientsStore";
import { useNow } from "./useNow";
import type { Pasient } from "../utils/mapping/epj";

type Props = {
  canCreate: boolean;
  onNewPatient: () => void;
  onOpenJournal: (pasient: Pasient) => void;
};

export function PatientsPage({ canCreate, onNewPatient, onOpenJournal }: Props) {
  const status = usePatientsStore((s) => s.status);
  const query = usePatientsStore((s) => s.query);
  const view = usePatientsStore((s) => s.view);
  const page = usePatientsStore((s) => s.page);
  const lastKonsultasjon = usePatientsStore((s) => s.lastKonsultasjon);
  const patients = usePatientsStore((s) => s.patients);
  const recentIds = usePatientsStore((s) => s.recentIds);
  const scoped = useMemo(
    () => scopePatients(patients, recentIds, view),
    [patients, recentIds, view],
  );
  const recentCount = useMemo(
    () => scopePatients(patients, recentIds, "recent").length,
    [patients, recentIds],
  );
  const { load, setQuery, setView, setPage } = usePatientsStore.getState();
  const now = useNow(60_000);

  useEffect(() => {
    if (usePatientsStore.getState().status === "idle") void load();
  }, [load]);

  const filtered = filterPatients(scoped, query);
  const trimmedQuery = query.trim();
  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const current = Math.min(page, pages);
  const start = (current - 1) * PAGE_SIZE;
  const rows = filtered.slice(start, start + PAGE_SIZE);

  return (
    <>
      <div className="xp-pagehead">
        <XpIcon name="pasienter" size={32} />
        <h1 className="xp-h1">{copy["s3.title"]}</h1>
        <span className="grow" />
        {canCreate && (
          <>
            <Button onClick={onNewPatient}>{copy["s3.newPatient"]}</Button>
            <Badge tone="info">{copy["badge.localOnly"]}</Badge>
          </>
        )}
      </div>
      <div className="xp-pagehead">
        <SegmentedControl<PatientsView>
          label={copy["s3.segment.label"]}
          value={view}
          onChange={setView}
          options={[
            { value: "mine", label: copy["s3.segment.mine"](patients.length) },
            { value: "recent", label: copy["s3.segment.recent"](recentCount) },
          ]}
        />
        {trimmedQuery && (
          <>
            <span>{copy["s3.filtered"](trimmedQuery)}</span>
            <Button variant="link" onClick={() => setQuery("")}>
              {copy["s3.clearSearch"]}
            </Button>
          </>
        )}
      </div>
      {status === "loading" || status === "idle" ? (
        <p role="status" className="xp-notice">
          {copy["common.loading"]}
        </p>
      ) : status === "error" ? (
        <div className="xp-note error" role="alert">
          <XpIcon name="feil" />
          <span>{copy["s3.loadError"]}</span>
          <Button onClick={() => void load()}>{copy["s1.error.retry"]}</Button>
        </div>
      ) : filtered.length === 0 ? (
        <div className="xp-card xp-empty">
          {trimmedQuery ? (
            <>
              <p>
                <b>{copy["s3.empty.search.title"](trimmedQuery)}</b>
              </p>
              <p>{copy["s3.empty.search.body"]}</p>
              <Button onClick={() => setQuery("")}>{copy["s3.clearSearch"]}</Button>
            </>
          ) : view === "recent" && patients.length > 0 ? (
            <p>
              <b>{copy["s3.empty.recent"]}</b>
            </p>
          ) : (
            <>
              <p>
                <b>{copy["s3.empty.none.title"]}</b>
              </p>
              <p>{copy["s3.empty.none.body"]}</p>
              {canCreate && (
                <Button onClick={onNewPatient}>{copy["s3.newPatient"]}</Button>
              )}
            </>
          )}
        </div>
      ) : (
        <div className="xp-card xp-tablewrap">
          <table className="xp-list" aria-label={copy["s3.title"]}>
            <thead>
              <tr>
                <th scope="col">
                  <span className="h">{copy["s3.col.name"]}</span>
                </th>
                <th scope="col">
                  <span className="h">{copy["s3.col.born"]}</span>
                </th>
                <th scope="col">
                  <span className="h">{copy["s3.col.fnr"]}</span>
                </th>
                <th scope="col">
                  <span className="h">{copy["s3.col.last"]}</span>
                </th>
                <th scope="col">
                  <span className="h sr-only">{copy["s3.col.action.sr"]}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((pasient) => {
                const birthDate = birthDateOf(pasient);
                const last = lastKonsultasjon[pasient.id];
                return (
                  <tr key={pasient.id}>
                    <td>
                      <strong>{fullName(pasient)}</strong>
                    </td>
                    <td>
                      {birthDate
                        ? copy["s3.col.bornValue"](
                            formatDate(birthDate),
                            ageOn(birthDate, now),
                          )
                        : "–"}
                    </td>
                    <td>
                      <span className="mono" aria-hidden="true">
                        {maskPersonident(pasient.personident)}
                      </span>
                      <span className="sr-only">
                        {copy["s3.col.fnr.sr"]} {personidentTail(pasient.personident)}
                      </span>
                    </td>
                    <td>
                      {!last
                        ? "–"
                        : last.status === "PÅGÅENDE"
                          ? copy["s3.col.last.ongoing"]
                          : formatDate(last.tidspunkt)}
                    </td>
                    <td>
                      <IconButton
                        icon="journal"
                        label={copy["s3.open.aria"](fullName(pasient))}
                        title={copy["s3.open.tooltip"]}
                        onClick={() => onOpenJournal(pasient)}
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <PatientsPager
            from={start + 1}
            to={start + rows.length}
            total={filtered.length}
            unfilteredTotal={scoped.length}
            page={current}
            pages={pages}
            onPage={setPage}
          />
        </div>
      )}
    </>
  );
}
