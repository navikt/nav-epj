import { vi } from "vitest";
import { useAppRunStore } from "./appRunStore";
import { useAppsStore } from "./appsStore";
import { useJournalStore } from "./journalStore";
import type { App, Consultation, Patient, PatientListItem } from "../utils/mapping/ehr";

export const sykInn: App = {
  clientId: "syk-inn",
  name: "Sykmelding",
  description: "Skriv sykmelding og send den til Nav.",
  icon: "sick-note",
  launchMode: "iframe",
  launchUri: "https://syk.example/fhir",
  tokenEndpointAuthMethod: "client_secret_basic",
  jwksUri: null,
  redirectUris: ["https://syk.example/callback"],
  scopes: ["launch", "openid"],
};

export const validator: App = {
  ...sykInn,
  clientId: "validator",
  name: "Validator",
  description: "Tester SMART on FHIR.",
  icon: "validator",
  launchMode: "ask",
};

export const newTab: App = {
  ...sykInn,
  clientId: "ny-fane",
  name: "Fanen",
  launchMode: "tab",
};

export const ola: PatientListItem = {
  id: "p1",
  givenName: "Ola",
  familyName: "Nordmann",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: "1990-01-01",
  gender: "MALE",
  selectable: true,
};

export const kari: PatientListItem = {
  ...ola,
  id: "p2",
  givenName: "Kari",
  familyName: "Hansen",
};

export const ongoingConsultation: Consultation = {
  id: "k1",
  patientId: "p1",
  hpr: ["9144889"],
  clinicalNotes: [],
  diagnoses: [],
  startedAt: "2026-09-30T09:14:00",
  endedAt: null,
  status: "IN_PROGRESS",
  reasonForVisit: null,
};

export function seedJournal(
  patient: Patient = ola,
  consultations: Consultation[] = [ongoingConsultation],
) {
  useJournalStore.setState({
    patientId: patient.id,
    patient,
    consultations,
    status: "ready",
  });
}

export function seedApps(apps: App[] = [sykInn, validator]) {
  useAppsStore.setState({ apps, status: "ready" });
}

export function seedRun(patient: Patient = ola, app: App = sykInn) {
  useAppRunStore.getState().startRun({
    clientId: app.clientId,
    name: app.name,
    patient,
    consultation: {
      id: ongoingConsultation.id,
      startedAt: ongoingConsultation.startedAt,
    },
    startedAt: new Date(2026, 8, 30, 9, 14),
  });
}

export function stubLaunch(
  responses: Array<{ status?: number; body: unknown } | "network">,
) {
  const queue = [...responses];
  const fn = vi.fn(async () => {
    const next = queue.shift() ?? queue[0];
    if (next === "network") throw new TypeError("network");
    const status = next?.status ?? 200;
    return { ok: status < 400, status, json: async () => next?.body };
  });
  vi.stubGlobal("fetch", fn);
  return fn;
}

export const launchOk = (n = 1) => ({
  body: {
    launchUrl: `https://syk.example/fhir/?iss=https://epj.example/fhir&launch=id-${n}`,
  },
});
