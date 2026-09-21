import { createFileRoute, Link, Outlet } from '@tanstack/react-router'
import { fetchPatient } from '@utils/fetch';
import { PasientSchema } from '@utils/mapping/epj';

export const Route = createFileRoute('/patients/$patientId')({
  loader: async ({ params }) => {
    const pasient = await fetchPatient(params.patientId);
    return { pasient };
  },
  component: PatientLayout,
})

function PatientLayout() {
  const data = Route.useLoaderData();
  const patient = PasientSchema.safeParse(data.pasient);

  if (patient.error) {
    return <div>Feil ved lasting av pasient</div>;
  }

  return (
    <div>
      <div className="flex justify-between pb-4"><span>Navn {`${patient.data?.fornavn} ${patient.data?.etternavn}`}, fnr: {patient.data?.fnr}</span><span><Link className="aksel-link" to="/patients" >Tilbake til pasientoversikt</Link></span></div>
      <Outlet />
    </div>
  )
}
