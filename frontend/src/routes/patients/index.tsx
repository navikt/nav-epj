import { Heading } from "@navikt/ds-react";
import { createFileRoute, Link } from "@tanstack/react-router";
import type { Pasient } from "@utils/mapping/epj";
import { useState, useEffect } from "react";

export const Route = createFileRoute("/patients/")({
  component: RouteComponent,
});

async function fetchPatients(): Promise<Pasient[]> {
  const res = await fetch("/api/patient").then((res) => res.json());
  return res;
}

function RouteComponent() {
  const [patients, setPatients] = useState<Pasient[]>([]);

  useEffect(() => {
    fetchPatients().then((res) => setPatients(res));
  }, []);
  return (
    <>
      {patients.length > 0 && (
        <Heading level="1" size="xlarge">
          Pasienter
        </Heading>
      )}
      {patients.map((patient) => (
        <div key={patient.id}>
          <Link to="/patients/$patientId" params={{ patientId: patient.id }}>
            {patient.navn}
          </Link>
        </div>
      ))}
    </>
  );
}
