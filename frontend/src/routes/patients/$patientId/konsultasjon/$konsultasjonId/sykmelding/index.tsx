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
      <Heading level="1" size="large">TODO: Launch syk-inn</Heading>
      
    </div>
  )
}
