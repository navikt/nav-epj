import { createFileRoute } from "@tanstack/react-router";
import { PatientSchema, type FhirPatient } from "@utils/mapping/fhir";

export const Route = createFileRoute("/patient/$patientId/")({
  loader: async ({ params }) => {
    return await fetchPatient(params.patientId);
  },
  component: RouteComponent,
});

async function fetchPatient(id: string): Promise<FhirPatient> {
  const res = await fetch(`/fhir/Patient/${id}`).then((res) => res.json());
  return res;
}

function RouteComponent() {
  const { patientId } = Route.useParams();
  const data = Route.useLoaderData();
  const patient = PatientSchema.safeParse(data);
  return (
    <div>
      Hello "/patient/{patientId}/"! -{" "}
      {patient.success
        ? patient.data.name[0]?.given.join(" ") +
          " " +
          patient.data.name[0]?.family
        : "Loading..."}
    </div>
  );
}
