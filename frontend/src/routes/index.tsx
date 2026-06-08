import type { Patient } from "@utils/mapping/fhir";
import { Heading } from "@navikt/ds-react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";

export const Route = createFileRoute("/")({
  component: Index,
});

async function fetchPatients(): Promise<Patient[]> {
  return await fetch("/api/patient").then((res) => res.json());
}

function Index() {
  const [patients, setPatients] = useState<Patient[]>([]);

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
                {patient.name}
            </Link>
          </div>
        ))}
      </main>
    </>
  );
}
