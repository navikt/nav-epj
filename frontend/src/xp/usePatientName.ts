import { useEffect, useState } from "react";
import { fetchPatient } from "./api";
import { copy } from "./copy";
import { fullName } from "./patientInfo";
import { usePatientsStore } from "./patientsStore";

export function usePatientLabel(patientId: string | null) {
  const listed = usePatientsStore((s) =>
    s.patients.find((p) => p.id === patientId),
  );
  const [fetched, setFetched] = useState<{ id: string; name: string } | null>(
    null,
  );

  useEffect(() => {
    if (listed || patientId === null) return;
    let active = true;
    fetchPatient(patientId)
      .then((p) => active && setFetched({ id: patientId, name: fullName(p) }))
      .catch(() => active && setFetched({ id: patientId, name: patientId }));
    return () => {
      active = false;
    };
  }, [patientId, listed]);

  if (patientId === null) return { name: "", loaded: false };
  if (listed) return { name: fullName(listed), loaded: true };
  if (fetched?.id === patientId) return { name: fetched.name, loaded: true };
  return { name: copy["common.loading"], loaded: false };
}

export function usePatientName(patientId: string | null) {
  return usePatientLabel(patientId).name;
}
