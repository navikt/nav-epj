import { Heading, Table } from "@navikt/ds-react";
import { createFileRoute, Link } from "@tanstack/react-router";
import {
  type Pasient,
} from "@utils/mapping/epj";
import { useState, useEffect } from "react";
import { OpprettPasient } from "../../components/OpprettPasient/OpprettPasient";
import { isLocalhost } from "@utils/env";

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

      {isLocalhost() && 
        <OpprettPasient lastPasienter={lastPasienter} />
      }
      
      {patients.length === 0 && <div>Ingen pasienter funnet</div>}
      {patients.length > 0 && <Table>
        <Table.Header>
          <Table.Row>
            <Table.HeaderCell>Fornavn</Table.HeaderCell>
            <Table.HeaderCell>Etternavn</Table.HeaderCell>
            <Table.HeaderCell>Fødselsnummer</Table.HeaderCell>
            <Table.HeaderCell />
          </Table.Row>
        </Table.Header>
        <tbody>
          {patients.map((patient) => (
            <Table.Row key={patient.id}>
              <Table.DataCell>{patient.fornavn}</Table.DataCell>
              <Table.DataCell>{patient.etternavn}</Table.DataCell>
              <Table.DataCell>{patient.fnr}</Table.DataCell>
              <Table.DataCell>
                <Link className="aksel-link" to="/patients/$patientId" params={{ patientId: patient.id }}>
                  Gå til pasient
                </Link>
              </Table.DataCell>
            </Table.Row>
          ))}
        </tbody>
      </Table>}

      
    </>
  );
}
