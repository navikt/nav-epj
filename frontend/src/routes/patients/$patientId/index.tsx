import { Button, Heading, Table } from "@navikt/ds-react";
import { createFileRoute, Link, useRouter } from "@tanstack/react-router";
import {
  KonsultasjonSchema,
  PasientSchema,
} from "@utils/mapping/epj";
import { fetchPatient, fetchKonsultasjoner } from "@utils/fetch";
import { format } from "date-fns";

export const Route = createFileRoute("/patients/$patientId/")({
  loader: async ({ params }) => {
    const pasient = await fetchPatient(params.patientId);
    const konsultasjoner = await fetchKonsultasjoner(params.patientId);
    return { pasient, konsultasjoner };
  },
  component: RouteComponent,
});



async function opprettKonsultasjon(patientId: string) {
  return await fetch(`/api/patients/${patientId}/konsultasjoner`, { method: 'POST' }).then((res) => res.json())
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
    const res = await opprettKonsultasjon(patientId)
    console.log(res);
    router.invalidate()
  }

  if (konsultasjoner.error) {
    console.error(konsultasjoner.error.message);
    return <div>Feil ved lasting av konsultasjoner</div>;
  }


  return (
    <div className="flex flex-col items-start gap-4">
      {(patient.success && konsultasjoner.success) &&
        <div>
          <Button variant={'primary'} onClick={() => handleOnClickOpprettKonsultasjon()}>Opprett ny konsultasjon</Button>
          <Heading size="medium" level="2">
            Konsultasjoner
          </Heading>
          {konsultasjoner.data.length === 0 && <div>Ingen konsultasjoner funnet</div>}
          {konsultasjoner.data.length > 0 && 
          <Table>
            <Table.Header>
              <Table.Row>
                <Table.HeaderCell>Startet</Table.HeaderCell>
                <Table.HeaderCell>Status</Table.HeaderCell>
                <Table.HeaderCell />
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {konsultasjoner.data.map((konsultasjon) => (
                <Table.Row key={konsultasjon.id}>
                  <Table.DataCell>
                    {format(konsultasjon.startetTidspunkt, "dd.MM.yyyy HH:mm")}
                  </Table.DataCell>
                  <Table.DataCell>{konsultasjon.status}</Table.DataCell>
                  <Table.DataCell>
                    <Link className="aksel-link" to="/patients/$patientId/konsultasjon/$konsultasjonId" params={{ patientId, konsultasjonId: konsultasjon.id }}>
                      Se konsultasjon
                    </Link>
                  </Table.DataCell>
                </Table.Row>
              ))}
            </Table.Body>
          </Table>}
        </div>
      }
      

    </div>
  );
}
