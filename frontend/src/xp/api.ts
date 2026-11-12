import { expireSession } from "./sessionExpiry";
import { notifySessionOrgConflict } from "./sessionOrgConflict";
import {
  ActivePatientSchema,
  AppSchema,
  CapabilityStatementSchema,
  ConsultationSchema,
  LaunchErrorSchema,
  LaunchResponseSchema,
  MeasurementSchema,
  PatientListItemSchema,
  PatientSchema,
  SessionOrgSchema,
  SessionSchema,
  SmartConfigurationSchema,
  type ActivePatient,
  type App,
  type CapabilityStatement,
  type Consultation,
  type Measurement,
  type CreatePatientRequest,
  type Patient,
  type PatientListItem,
  type Session,
  type SessionOrg,
  type SmartConfiguration,
} from "../utils/mapping/ehr";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(String(status));
    this.status = status;
  }
}

export class LaunchError extends ApiError {
  readonly code:
    | "NO_ACTIVE_PATIENT"
    | "NO_ACTIVE_ENCOUNTER"
    | "UNKNOWN_APP"
    | "PATIENT_MISMATCH";

  constructor(status: number, code: LaunchError["code"]) {
    super(status);
    this.code = code;
  }
}

export type DiagnosisKey = { code: string; system: string };

export type SaveConsultationRequest = {
  consultationId: string;
  diagnoses: DiagnosisKey[];
  clinicalNote: string | null;
  finalize: boolean;
};

export const callOf = (url: string, init?: RequestInit) =>
  `${init?.method ?? "GET"} ${url}`;

async function request(url: string, init?: RequestInit) {
  const response = await fetch(url, init);
  if (response.status === 401) expireSession(callOf(url, init));
  if (response.status === 409) notifySessionOrgConflict();
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

export async function fetchSessionOrg(): Promise<SessionOrg> {
  const response = await request("/api/session-org");
  return SessionOrgSchema.parse(await response.json());
}

export async function chooseSessionOrg(
  orgNumber: string,
): Promise<SessionOrg> {
  const response = await request(
    "/api/session-org",
    jsonInit("PUT", { orgNumber }),
  );
  return SessionOrgSchema.parse(await response.json());
}

export async function fetchPatients(): Promise<PatientListItem[]> {
  const response = await request("/api/patient");
  return PatientListItemSchema.array().parse(await response.json());
}

export async function fetchPatient(id: string): Promise<Patient> {
  const response = await request(`/api/patient/${encodeURIComponent(id)}`);
  return PatientSchema.parse(await response.json());
}

export async function createPatient(
  body: CreatePatientRequest,
): Promise<Patient> {
  const response = await request("/api/patient", jsonInit("POST", body));
  return PatientSchema.parse(await response.json());
}

export async function fetchConsultations(
  patientId: string,
): Promise<Consultation[]> {
  const response = await request(
    `/api/patients/${encodeURIComponent(patientId)}/consultations`,
  );
  return ConsultationSchema.array().parse(await response.json());
}

export async function fetchMeasurements(patientId: string): Promise<Measurement[]> {
  const response = await request(
    `/api/patient/${encodeURIComponent(patientId)}/measurements`,
  );
  return MeasurementSchema.array().parse(await response.json());
}

export async function startConsultation(
  patientId: string,
): Promise<Consultation> {
  const response = await request(
    `/api/patients/${encodeURIComponent(patientId)}/consultations`,
    { method: "POST" },
  );
  return ConsultationSchema.parse(await response.json());
}

export async function saveConsultation(
  patientId: string,
  body: SaveConsultationRequest,
): Promise<void> {
  await request(
    `/api/patients/${encodeURIComponent(patientId)}/consultations`,
    jsonInit("PATCH", body),
  );
}

export async function cancelConsultation(
  patientId: string,
  consultationId: string,
): Promise<void> {
  await request(
    `/api/patients/${encodeURIComponent(patientId)}/consultations/${encodeURIComponent(consultationId)}/cancel`,
    { method: "POST" },
  );
}

export async function deleteClinicalNote(noteId: string): Promise<void> {
  try {
    await request(`/api/clinical-notes/${encodeURIComponent(noteId)}`, {
      method: "DELETE",
    });
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 404)) throw error;
  }
}

export async function fetchApps(): Promise<App[]> {
  const response = await request("/api/apps");
  return AppSchema.array().parse(await response.json());
}

export async function fetchSession(): Promise<Session> {
  const response = await request("/api/session");
  return SessionSchema.parse(await response.json());
}

export async function fetchSmartConfiguration(): Promise<SmartConfiguration> {
  const response = await request("/fhir/.well-known/smart-configuration");
  return SmartConfigurationSchema.parse(await response.json());
}

export async function fetchCapabilityStatement(): Promise<CapabilityStatement> {
  const response = await request("/fhir/metadata");
  return CapabilityStatementSchema.parse(await response.json());
}

export async function fetchActivePatient(): Promise<ActivePatient | null> {
  const response = await request("/api/active-patient");
  if (response.status === 204) return null;
  return ActivePatientSchema.parse(await response.json());
}

export async function putActivePatient(
  patientId: string,
): Promise<ActivePatient> {
  const response = await request(
    "/api/active-patient",
    jsonInit("PUT", { patientId }),
  );
  return ActivePatientSchema.parse(await response.json());
}

export async function launchApp(
  appId: string,
  patientId: string,
): Promise<string> {
  const init = jsonInit("POST", { appId, patientId });
  const response = await fetch("/api/launch", init);
  if (response.status === 401) expireSession(callOf("/api/launch", init));
  if (response.status === 409) notifySessionOrgConflict();
  if (!response.ok) {
    const error = LaunchErrorSchema.safeParse(
      await response.json().catch(() => null),
    );
    if (error.success) throw new LaunchError(response.status, error.data.code);
    throw new ApiError(response.status);
  }
  return LaunchResponseSchema.parse(await response.json()).launchUrl;
}
