import { Heading } from '@navikt/ds-react'
import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute(
  '/patients/$patientId/konsultasjon/$konsultasjonId/sykmelding/',
)({
  component: RouteComponent,
})

function RouteComponent() {
  return (
    <div>
      <Heading level="1" size="large">(ny) Sykmelding</Heading>
      <iframe width="100%" height="800px" src='/fhir/launch?url=https://www.ekstern.dev.nav.no/samarbeidspartner/sykmelding/fhir/launch' />
    </div>
  )
}
