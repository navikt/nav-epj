import { Alert, Button, Heading, TextField, VStack } from "@navikt/ds-react";
import { createFileRoute, Link } from "@tanstack/react-router";
import {
  OpprettPasientSchema,
  type OpprettPasientRequest,
  type Pasient,
} from "@utils/mapping/epj";
import { useState, useEffect } from "react";

export const Route = createFileRoute("/patients/")({
  component: RouteComponent,
});

async function fetchPatients(): Promise<Pasient[]> {
  const res = await fetch("/api/patient").then((res) => res.json());
  return res;
}

async function opprettPasient(request: OpprettPasientRequest): Promise<Pasient> {
  const res = await fetch("/api/patient", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!res.ok) {
    throw new Error("Klarte ikke å opprette pasient");
  }
  return res.json();
}

function RouteComponent() {
  const [patients, setPatients] = useState<Pasient[]>([]);
  const [navn, setNavn] = useState("");
  const [fnr, setFnr] = useState("");
  const [feilmelding, setFeilmelding] = useState<string | null>(null);
  const [lagrer, setLagrer] = useState(false);

  function lastPasienter() {
    fetchPatients().then((res) => setPatients(res));
  }

  useEffect(() => {
    lastPasienter();
  }, []);

  async function handleOpprettPasient(e: React.FormEvent) {
    e.preventDefault();
    setFeilmelding(null);

    const parsed = OpprettPasientSchema.safeParse({ navn, fnr });
    if (!parsed.success) {
      setFeilmelding(parsed.error.issues[0].message);
      return;
    }

    setLagrer(true);
    try {
      await opprettPasient(parsed.data);
      setNavn("");
      setFnr("");
      lastPasienter();
    } catch {
      setFeilmelding("Kunne ikke opprette pasient. Sjekk at fødselsnummeret ikke allerede finnes.");
    } finally {
      setLagrer(false);
    }
  }

  return (
    <>
      <Heading level="1" size="xlarge">
        Pasienter
      </Heading>

      {patients.map((patient) => (
        <div key={patient.id}>
          <Link to="/patients/$patientId" params={{ patientId: patient.id }}>
            {patient.navn}
          </Link>
        </div>
      ))}

      <Heading level="2" size="medium" spacing>
        Opprett ny pasient
      </Heading>
      <form onSubmit={handleOpprettPasient}>
        <VStack gap="4" maxWidth="20rem">
          {feilmelding && <Alert variant="error">{feilmelding}</Alert>}
          <TextField
            label="Navn"
            value={navn}
            onChange={(e) => setNavn(e.target.value)}
          />
          <TextField
            label="Fødselsnummer"
            value={fnr}
            onChange={(e) => setFnr(e.target.value)}
          />
          <Button type="submit" loading={lagrer}>
            Opprett pasient
          </Button>
        </VStack>
      </form>
    </>
  );
}
