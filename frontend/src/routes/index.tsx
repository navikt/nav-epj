import type { FhirPatient } from "@utils/mapping/fhir";
import { Heading } from "@navikt/ds-react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";

export const Route = createFileRoute("/")({
  component: Index,
});

async function fetchPatients(): Promise<FhirPatient[]> {
  const res = await fetch("/fhir/Patient").then((res) => res.json());
  return res.entry.map((entry: any) => entry.resource);
}

function getFullPatientName(patient: FhirPatient): string {
  const givenNames = patient.name[0]?.given.join(" ") || "";
  const familyName = patient.name[0]?.family || "";
  return `${givenNames} ${familyName}`.trim();
}

function Index() {
  const [patients, setPatients] = useState<FhirPatient[]>([]);

  useEffect(() => {
    fetchPatients().then((res) => setPatients(res));
  }, []);

  return (
    <>
      <main>
        {patients.length > 0 && (
          <Heading level="1" size="xlarge">
            Pasienter
          </Heading>
        )}
        {patients.map((patient) => (
          <div key={patient.id}>
            <Link to="/patient/$patientId" params={{ patientId: patient.id }}>
              {getFullPatientName(patient)}
            </Link>
          </div>
        ))}
      </main>
    </>
  );
}
