import { createFileRoute } from "@tanstack/react-router";
import { PasientSchema, type Pasient } from "@utils/mapping/epj";

export const Route = createFileRoute("/patients/$patientId/")({
  loader: async ({ params }) => {
    return await fetchPatient(params.patientId);
  },
  component: RouteComponent,
});

async function fetchPatient(id: string): Promise<Pasient> {
  const res = await fetch(`/api/patient/${id}`).then((res) => res.json());
  return res;
}

function RouteComponent() {
  const { patientId } = Route.useParams();
  const data = Route.useLoaderData();
  const patient = PasientSchema.safeParse(data);
  return (
    <div>
      Hello "/patient/{patientId}/"! -{" "}
      {patient.success ? patient.data.navn : "Loading..."}
    </div>
  );
}
