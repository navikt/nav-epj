import { BodyLong, Heading } from "@navikt/ds-react";
import { createFileRoute, Link } from "@tanstack/react-router";

export const Route = createFileRoute("/")({
  component: Index,
});

function Index() {
  return (
    <>
      <Heading level="1" size="xlarge">
        Velkommen til Nav EPJ!
      </Heading>
      <BodyLong spacing>
        Dette er en demoapplikasjon for å vise integrasjon mellom EPJ system og
        ny sykmeldingsløsning over SMART on Fhir
      </BodyLong>
      <Link to="/patients">Se pasienter</Link>
    </>
  );
}
