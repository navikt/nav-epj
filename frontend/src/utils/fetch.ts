import type { Pasient } from "./mapping/epj";

export async function fetchPatient(id: string): Promise<Pasient> {
  return await fetch(`/api/patient/${id}`).then((res) => res.json());
}

export async function fetchKonsultasjoner(patientId: string) {
  return await fetch(`/api/patients/${patientId}/konsultasjoner`).then((res) =>
    res.json(),
  );
}

export async function fetchKonsultasjon(konsultasjonId: string) {
  return await fetch(`/api/konsultasjon/${konsultasjonId}`).then((res) => res.json());
}