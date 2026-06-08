import { createFileRoute } from "@tanstack/react-router";
import { Box, Button, Heading, HStack, TextField, VStack } from "@navikt/ds-react";
import { useState } from "react";
import { PatientSchema, type Patient } from "@utils/mapping/fhir";

export const Route = createFileRoute("/patient/$patientId/")({
  loader: async ({ params }) => {
    return await fetchPatient(params.patientId);
  },
  component: RouteComponent,
});

async function fetchPatient(id: string): Promise<Patient> {
  return await fetch(`/api/patient/${id}`).then((res) => res.json());
}

function LaunchBox({ patientId }: { patientId: string }) {
  const [url, setUrl] = useState("");

  function handleLaunch() {
    if (!url.trim()) return;
    const params = new URLSearchParams({
      url: url.trim(),
      patientId,
    });
    const target = `/fhir/launch?${params.toString()}`;
    window.location.href = target;
  }

  return (
    <Box background="surface-subtle" borderRadius="medium" padding="space-16">
      <VStack gap="4">
        <Heading level="2" size="small">
          Start ekstern app
        </Heading>
        <HStack gap="4" align="end">
          <TextField
            label="App launch-URL"
            placeholder="https://app.example.com/launch"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            style={{ minWidth: "360px" }}
          />
          <Button variant="primary" onClick={handleLaunch} disabled={!url.trim()}>
            Launch
          </Button>
        </HStack>
      </VStack>
    </Box>
  );
}

function RouteComponent() {
  const { patientId } = Route.useParams();
  const data = Route.useLoaderData();
  const patient = PatientSchema.safeParse(data);

  const patientName = patient.data?.name

  return (
    <Box paddingBlock="space-16" paddingInline="space-24">
      <VStack gap="8">
        <Heading level="1" size="large">
          {patientName}
        </Heading>
        <LaunchBox patientId={patientId} />
      </VStack>
    </Box>
  );
}
