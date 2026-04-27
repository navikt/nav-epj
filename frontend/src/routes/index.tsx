import { Heading } from "@navikt/ds-react";
import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import z from "zod";

export const Route = createFileRoute("/")({
  component: Index,
});

const Patient = z.object({
  id: z.string(),
  name: z.array(
    z.object({
      given: z.array(z.string()),
      family: z.string(),
    }),
  ),
});

async function fetchPatients(): Promise<z.infer<typeof Patient>[]> {
  const res = await fetch("/fhir/Patient").then((res) => res.json());
  return res.entry.map((entry: any) => entry.resource);
}

function getFullPatientName(patient: z.infer<typeof Patient>): string {
  const givenNames = patient.name[0]?.given.join(" ") || "";
  const familyName = patient.name[0]?.family || "";
  return `${givenNames} ${familyName}`.trim();
}

function Index() {
  const [patients, setPatients] = useState<z.infer<typeof Patient>[]>([]);

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
        {patients.map((patient, key) => (
          <div key={patient.id}>{getFullPatientName(patient)}</div>
        ))}
      </main>
    </>
  );
}
