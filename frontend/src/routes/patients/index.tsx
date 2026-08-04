import { Alert, Button, Heading, TextField, VStack } from "@navikt/ds-react";
import { createFileRoute, Link } from "@tanstack/react-router";
import {
  OpprettPasientSchema,
  type OpprettPasientRequest,
  type Pasient,
} from "@utils/mapping/epj";
import { useState, useEffect } from "react";
import { OpprettPasient } from "../../components/OpprettPasient/OpprettPasient";

export const Route = createFileRoute("/patients/")({
  component: RouteComponent,
});

async function fetchPatients(): Promise<Pasient[]> {
  const res = await fetch("/api/patient").then((res) => res.json());
  return res;
}


function RouteComponent() {
  const [patients, setPatients] = useState<Pasient[]>([]);


  function lastPasienter() {
    fetchPatients().then((res) => setPatients(res));
  }

  useEffect(() => {
    lastPasienter();
  }, []);

  return (
    <>
      <Heading level="1" size="xlarge">
        Pasienter
      </Heading>

      {patients.map((patient) => (
        <div key={patient.id}>
          <Link className="aksel-link" to="/patients/$patientId" params={{ patientId: patient.id }}>
            {patient.navn}
          </Link>
        </div>
      ))}

      <OpprettPasient lastPasienter={lastPasienter} />
    </>
  );
}
