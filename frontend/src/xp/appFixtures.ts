import { vi } from "vitest";
import { useAppRunStore } from "./appRunStore";
import { useAppsStore } from "./appsStore";
import { useJournalStore } from "./journalStore";
import type { App, Konsultasjon, Pasient } from "../utils/mapping/epj";

export const sykInn: App = {
  clientId: "syk-inn",
  navn: "Sykmelding",
  beskrivelse: "Skriv sykmelding og send den til Nav.",
  ikon: "sykmelding",
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
  navn: "Validator",
  beskrivelse: "Tester SMART on FHIR.",
  ikon: "validator",
  launchMode: "ask",
};

export const nyFane: App = {
  ...sykInn,
  clientId: "ny-fane",
  navn: "Fanen",
  launchMode: "tab",
};

export const ola: Pasient = {
  id: "p1",
  fornavn: "Ola",
  etternavn: "Nordmann",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: "1990-01-01",
  gender: "MALE",
};

export const kari: Pasient = {
  ...ola,
  id: "p2",
  fornavn: "Kari",
  etternavn: "Hansen",
};

export const ongoingKonsultasjon: Konsultasjon = {
  id: "k1",
  pasientId: "p1",
  hpr: ["9144889"],
  journalnotat: [],
  diagnoser: [],
  startetTidspunkt: "2026-09-30T09:14:00",
  avsluttetTidspunkt: null,
  status: "PÅGÅENDE",
  problemstilling: null,
};

export function seedJournal(
  patient: Pasient = ola,
  konsultasjoner: Konsultasjon[] = [ongoingKonsultasjon],
) {
  useJournalStore.setState({
    patientId: patient.id,
    patient,
    konsultasjoner,
    status: "ready",
  });
}

export function seedApps(apps: App[] = [sykInn, validator]) {
  useAppsStore.setState({ apps, status: "ready" });
}

export function seedRun(patient: Pasient = ola, app: App = sykInn) {
  useAppRunStore.getState().startRun({
    clientId: app.clientId,
    navn: app.navn,
    patient,
    konsultasjon: {
      id: ongoingKonsultasjon.id,
      startetTidspunkt: ongoingKonsultasjon.startetTidspunkt,
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
