import { useEffect, useState } from "react";
import { fetchPatient } from "./api";
import { copy } from "./copy";
import { fullName } from "./patientInfo";
import { usePatientsStore } from "./patientsStore";

export function usePatientName(patientId: string) {
  const listed = usePatientsStore((s) =>
    s.patients.find((p) => p.id === patientId),
  );
  const [fetched, setFetched] = useState<{ id: string; name: string } | null>(
    null,
  );

  useEffect(() => {
    if (listed) return;
    let active = true;
    fetchPatient(patientId)
      .then((p) => active && setFetched({ id: patientId, name: fullName(p) }))
      .catch(() => active && setFetched({ id: patientId, name: patientId }));
    return () => {
      active = false;
    };
  }, [patientId, listed]);

  if (listed) return fullName(listed);
  return fetched?.id === patientId ? fetched.name : copy["common.loading"];
}
