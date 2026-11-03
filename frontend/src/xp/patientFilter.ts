import type { Patient } from "../utils/mapping/epj";
import { fullName } from "./patientInfo";

export function filterPatients<T extends Patient>(patients: T[], query: string) {
  const needle = query.trim().toLowerCase();
  if (!needle) return patients;
  const digits = needle.replace(/\s/g, "");
  return patients.filter(
    (patient) =>
      fullName(patient).toLowerCase().includes(needle) ||
      patient.personident.includes(digits),
  );
}
