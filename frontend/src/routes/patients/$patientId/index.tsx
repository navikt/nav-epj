import { Button } from "@navikt/ds-react";
import { createFileRoute, Link, useRouter } from "@tanstack/react-router";
import {
  KonsultasjonSchema,
  PasientSchema,
  type Pasient,
} from "@utils/mapping/epj";

export const Route = createFileRoute("/patients/$patientId/")({
  loader: async ({ params }) => {
    const pasient = await fetchPatient(params.patientId);
    const konsultasjoner = await fetchKonsultasjoner(params.patientId);
    return { pasient, konsultasjoner };
  },
  component: RouteComponent,
});

async function fetchPatient(id: string): Promise<Pasient> {
  return await fetch(`/api/patient/${id}`).then((res) => res.json());
}

async function fetchKonsultasjoner(patientId: string) {
  return await fetch(`/api/patient/${patientId}/konsultasjoner`).then((res) =>
    res.json(),
  );
}

async function opprettKonsultasjon(patientId: string) {
  return await fetch(`/api/patient/${patientId}/konsultasjon`, { method: 'POST'}).then((res) => res.json())
}

function RouteComponent() {
  const router = useRouter()
  const { patientId } = Route.useParams();
  const data = Route.useLoaderData();
  const patient = PasientSchema.safeParse(data.pasient);
  const konsultasjoner = KonsultasjonSchema.array().safeParse(
    data.konsultasjoner,
  );

  async function handleOnClickOpprettKonsultasjon() {
    await opprettKonsultasjon(patientId)
    router.invalidate()
  }

  
  return (
    <div>
      
      {(patient.success && konsultasjoner.success) && 
      <div>
        Pasientnavn: {patient.data?.navn}
        <ul>
          {konsultasjoner.data.map((konsultasjon) => (
            <li key={konsultasjon.id}><Link to="/patients/$patientId/konsultasjon/$konsultasjonId" params={{patientId, konsultasjonId: konsultasjon.id}}>{konsultasjon.startetTidspunkt} - {konsultasjon.status}</Link></li>
          ))}
        </ul>
      </div>
      }
      <Button variant={'primary'} onClick={() => handleOnClickOpprettKonsultasjon()}>Opprett ny konsultasjon</Button>
      
    </div>
  );
}
