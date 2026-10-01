import { useId, useMemo } from "react";
import { useNavigate } from "@tanstack/react-router";
import { format } from "date-fns";
import { Button } from "./Button";
import { Card } from "./Card";
import { Note } from "./Note";
import { copy } from "./copy";
import { useActivePatientStore } from "./activePatientStore";
import { accessExpiry, isOutdatedFor, useAppRunStore } from "./appRunStore";
import { useCurrentUser } from "./currentUser";
import { useJournalStore } from "./journalStore";
import { birthDateOf, formatDate, fullName } from "./patientInfo";
import { scopePatients, usePatientsStore } from "./patientsStore";
import { SEARCH_INPUT_ID } from "./shellContext";
import { openPatientsTab } from "./tabRoutes";
import { useOpenJournal } from "./useOpenJournal";

export function StartTab() {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const openJournal = useOpenJournal();
  const patients = usePatientsStore((s) => s.patients);
  const recentIds = usePatientsStore((s) => s.recentIds);
  const lastKonsultasjon = usePatientsStore((s) => s.lastKonsultasjon);
  const tabApps = useAppRunStore((s) => s.tabApps);
  const journalPatientId = useJournalStore((s) => s.patientId);
  const activePatientId = useActivePatientStore((s) => s.activeId);
  const recent = useMemo(
    () => scopePatients(patients, recentIds, "recent"),
    [patients, recentIds],
  );
  const findId = useId();
  const recentId = useId();
  const tabAppsId = useId();
  const aboutId = useId();

  if (!user) return <p role="status">{copy["common.loading"]}</p>;

  return (
    <>
      <h1 className="xp-h1">{copy["s2.greeting"](user.navn)}</h1>
      <div className="xp-start-grid">
        <Card heading={copy["s2.find.title"]} headingId={findId}>
          <p>{copy["s2.find.body"]}</p>
          <div className="xp-btnrow">
            <Button
              isDefault
              onClick={() => openPatientsTab((target) => void navigate(target))}
            >
              {copy["s2.find.open"]}
            </Button>
            <Button
              onClick={() => document.getElementById(SEARCH_INPUT_ID)?.focus()}
            >
              {copy["s2.find.search"]}
            </Button>
          </div>
        </Card>
        {recent.length > 0 && (
          <Card heading={copy["s2.recent.title"]} headingId={recentId}>
            <ul className="xp-recent-list">
              {recent.map((pasient) => {
                const birthDate = birthDateOf(pasient);
                const last = lastKonsultasjon[pasient.id];
                const siste =
                  !last
                    ? "–"
                    : last.status === "PÅGÅENDE"
                      ? copy["s3.col.last.ongoing"]
                      : formatDate(last.tidspunkt);
                return (
                  <li key={pasient.id}>
                    <Button variant="link" onClick={() => openJournal(pasient)}>
                      {fullName(pasient)}
                    </Button>
                    <span>
                      {copy["s2.recent.item"](
                        birthDate ? formatDate(birthDate) : "–",
                        siste,
                      )}
                    </span>
                  </li>
                );
              })}
            </ul>
          </Card>
        )}
        {tabApps.length > 0 && (
          <Card heading={copy["s2.tabApps.title"]} headingId={tabAppsId}>
            {tabApps.map((tabApp) => {
              const stale = isOutdatedFor(
                tabApp.patient.id,
                journalPatientId,
                activePatientId,
              );
              const tid = format(accessExpiry(tabApp.startedAt), "HH:mm");
              return (
                <Note key={tabApp.id} tone={stale ? "warn" : "info"}>
                  <strong>{tabApp.navn}</strong>{" "}
                  {stale
                    ? copy["s2.tabApps.stale"](tid)
                    : copy["s2.tabApps.running"](tid)}
                </Note>
              );
            })}
          </Card>
        )}
        <Card heading={copy["s2.about.title"]} headingId={aboutId}>
          <p>{copy["s2.about.body"]}</p>
        </Card>
      </div>
    </>
  );
}
