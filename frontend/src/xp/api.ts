import { expireSession } from "./sessionExpiry";
import {
  AppSchema,
  KonsultasjonSchema,
  LaunchErrorSchema,
  LaunchResponseSchema,
  PasientSchema,
  type App,
  type Konsultasjon,
  type OpprettPasientRequest,
  type Pasient,
} from "../utils/mapping/epj";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(String(status));
    this.status = status;
  }
}

export class LaunchError extends ApiError {
  readonly code: "NO_ACTIVE_PATIENT" | "NO_ACTIVE_ENCOUNTER" | "UNKNOWN_APP";

  constructor(status: number, code: LaunchError["code"]) {
    super(status);
    this.code = code;
  }
}

export type DiagnoseKey = { kode: string; system: string };

export type SaveKonsultasjonRequest = {
  konsultasjonId: string;
  diagnoser: DiagnoseKey[];
  journalNotat: string | null;
  ferdigstill: boolean;
};

export const callOf = (url: string, init?: RequestInit) =>
  `${init?.method ?? "GET"} ${url}`;

async function request(url: string, init?: RequestInit) {
  const response = await fetch(url, init);
  if (response.status === 401) expireSession(callOf(url, init));
  if (!response.ok) throw new ApiError(response.status);
  return response;
}

function jsonInit(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

export async function fetchPatients(): Promise<Pasient[]> {
  const response = await request("/api/patient");
  return PasientSchema.array().parse(await response.json());
}

export async function fetchPatient(id: string): Promise<Pasient> {
  const response = await request(`/api/patient/${encodeURIComponent(id)}`);
  return PasientSchema.parse(await response.json());
}

export async function createPatient(
  body: OpprettPasientRequest,
): Promise<Pasient> {
  const response = await request("/api/patient", jsonInit("POST", body));
  return PasientSchema.parse(await response.json());
}

export async function fetchKonsultasjoner(
  patientId: string,
): Promise<Konsultasjon[]> {
  const response = await request(
    `/api/patients/${encodeURIComponent(patientId)}/konsultasjoner`,
  );
  return KonsultasjonSchema.array().parse(await response.json());
}

export async function startKonsultasjon(
  patientId: string,
): Promise<Konsultasjon> {
  const response = await request(
    `/api/patients/${encodeURIComponent(patientId)}/konsultasjoner`,
    { method: "POST" },
  );
  return KonsultasjonSchema.parse(await response.json());
}

export async function saveKonsultasjon(
  patientId: string,
  body: SaveKonsultasjonRequest,
): Promise<void> {
  await request(
    `/api/patients/${encodeURIComponent(patientId)}/konsultasjoner`,
    jsonInit("PATCH", body),
  );
}

export async function fetchApps(): Promise<App[]> {
  const response = await request("/api/apps");
  return AppSchema.array().parse(await response.json());
}

export async function launchApp(appId: string): Promise<string> {
  const init = jsonInit("POST", { appId });
  const response = await fetch("/api/launch", init);
  if (response.status === 401) expireSession(callOf("/api/launch", init));
  if (!response.ok) {
    const error = LaunchErrorSchema.safeParse(
      await response.json().catch(() => null),
    );
    if (error.success) throw new LaunchError(response.status, error.data.code);
    throw new ApiError(response.status);
  }
  return LaunchResponseSchema.parse(await response.json()).launchUrl;
}
