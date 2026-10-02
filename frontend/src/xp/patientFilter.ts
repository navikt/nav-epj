import type { Pasient } from "../utils/mapping/epj";
import { fullName } from "./patientInfo";

export function filterPatients(patients: Pasient[], query: string) {
  const needle = query.trim().toLowerCase();
  if (!needle) return patients;
  const digits = needle.replace(/\s/g, "");
  return patients.filter(
    (pasient) =>
      fullName(pasient).toLowerCase().includes(needle) ||
      pasient.personident.includes(digits),
  );
}
