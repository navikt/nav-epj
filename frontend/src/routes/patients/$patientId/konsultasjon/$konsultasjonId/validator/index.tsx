import { Heading } from '@navikt/ds-react'
import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute(
  '/patients/$patientId/konsultasjon/$konsultasjonId/validator/',
)({
  component: RouteComponent,
})

function RouteComponent() {
  return (
    <div>
          <Heading level="1" size="large">Validering av Smart on FHIR</Heading>
          <iframe width="100%" height="800px" src='/fhir/launch?url=https://nav-on-fhir.ekstern.dev.nav.no/launch ' />
        </div>
  )
}
